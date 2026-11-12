package dev.ward.infrastructure.buildpolicy.node;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.domain.buildpolicy.ToolVersion;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.infrastructure.buildpolicy.BuildPaths;
import dev.ward.infrastructure.buildpolicy.WorkspaceMembers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads {@code package.json}: its dependencies and the package manager it declares. */
public final class NodeBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(NodeBuildReader.class.getName());
  private static final String FILE = "package.json";
  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
  private static final List<String> LOCKFILES =
      List.of("package-lock.json", "pnpm-lock.yaml", "yarn.lock", "bun.lock");
  private static final Pattern MANAGER = Pattern.compile("^(npm|pnpm|yarn|bun)@(\\d+(?:\\.\\d+)*)");

  private final JsonCodec codec;

  /** Creates the reader over the JSON codec. */
  public NodeBuildReader(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public List<String> modules(final Path root, final String dir) {
    final Map<?, ?> json = json(root, dir);
    Object workspaces = json.get("workspaces");
    if (workspaces instanceof Map<?, ?> map) {
      workspaces = map.get("packages");
    }
    final List<String> patterns = new ArrayList<>();
    if (workspaces instanceof List<?> list) {
      list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(patterns::add);
    }
    return WorkspaceMembers.expand(root, dir, patterns, FILE);
  }

  @Override
  public BuildModel read(final Path root, final String dir) {
    final Map<?, ?> json = json(root, dir);
    if (json.isEmpty()) {
      return BuildModel.EMPTY;
    }
    final String shown = BuildPaths.relative(dir, FILE);
    final String text = BuildPaths.read(BuildPaths.file(root, dir, FILE), shown);
    final boolean locked = BuildPaths.anyFile(root, dir, LOCKFILES);
    final List<Declaration> declarations = new ArrayList<>();
    for (final String section : SECTIONS) {
      if (json.get(section) instanceof Map<?, ?> entries) {
        final int start = Math.max(0, text.indexOf("\"" + section + "\""));
        entries.forEach(
            (name, version) ->
                declarations.add(
                    declaration(new Source(text, start, shown, locked), section, name, version)));
      }
    }
    final List<ToolVersion> tools = new ArrayList<>();
    if (json.get("packageManager") instanceof String manager) {
      final Matcher matcher = MANAGER.matcher(manager);
      if (matcher.find()) {
        tools.add(
            new ToolVersion(
                matcher.group(1),
                matcher.group(2),
                shown,
                BuildPaths.lineOf(text, "\"packageManager\"")));
      }
    }
    return new BuildModel(declarations, tools);
  }

  /** The text of a {@code package.json} and where its current section starts. */
  private record Source(String text, int start, String file, boolean locked) {}

  private static Declaration declaration(
      final Source source, final String section, final Object name, final Object version) {
    final int index = source.text.indexOf("\"" + name + "\"", source.start);
    final int line = index < 0 ? 0 : BuildPaths.lineAt(source.text, index);
    return new Declaration(
        BuildEcosystem.NODE,
        DeclarationKind.DEPENDENCY,
        String.valueOf(name),
        String.valueOf(version),
        section,
        "",
        source.file,
        line,
        source.locked ? Declaration.Flag.LOCKED : Declaration.Flag.NONE);
  }

  private Map<?, ?> json(final Path root, final String dir) {
    final Path file = BuildPaths.file(root, dir, FILE);
    if (!Files.isRegularFile(file)) {
      return Map.of();
    }
    final String shown = BuildPaths.relative(dir, FILE);
    LOGGER.log(Level.FINE, "event=build-policy-read-node file={0}", shown);
    try {
      final Object parsed = codec.fromJson(BuildPaths.read(file, shown), Object.class);
      if (parsed instanceof Map<?, ?> map) {
        return map;
      }
      throw new BuildFileException(shown, "the file is not a JSON object");
    } catch (final JsonCodecException exception) {
      throw new BuildFileException(shown, "the file is not valid JSON", exception);
    }
  }
}
