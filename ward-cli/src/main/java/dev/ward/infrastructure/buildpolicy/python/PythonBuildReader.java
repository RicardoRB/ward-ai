package dev.ward.infrastructure.buildpolicy.python;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.domain.buildpolicy.ToolVersion;
import dev.ward.infrastructure.buildpolicy.BuildPaths;
import dev.ward.infrastructure.buildpolicy.TomlLines;
import dev.ward.infrastructure.buildpolicy.WorkspaceMembers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads {@code pyproject.toml}: requirements, build requirements and uv's required version. */
public final class PythonBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(PythonBuildReader.class.getName());
  private static final String FILE = "pyproject.toml";
  private static final List<String> LOCKFILES = List.of("uv.lock", "poetry.lock");
  private static final Pattern REQUIREMENT =
      Pattern.compile("^\\s*([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:\\[[^]]*])?\\s*([^;@]*)(;.*)?$");
  private static final Pattern PLAIN_VERSION = Pattern.compile("\\d+(\\.\\d+)*");
  private static final String ANY = "*";

  @Override
  public List<String> modules(final Path root, final String dir) {
    final TomlParseResult toml = parse(root, dir);
    final TomlArray members = toml == null ? null : toml.getArray("tool.uv.workspace.members");
    if (members == null) {
      return List.of();
    }
    final List<String> patterns = new ArrayList<>();
    for (int index = 0; index < members.size(); index++) {
      patterns.add(members.getString(index));
    }
    return WorkspaceMembers.expand(root, dir, patterns, FILE);
  }

  @Override
  public BuildModel read(final Path root, final String dir) {
    final TomlParseResult toml = parse(root, dir);
    if (toml == null) {
      return BuildModel.EMPTY;
    }
    final String shown = BuildPaths.relative(dir, FILE);
    final boolean locked = BuildPaths.anyFile(root, dir, LOCKFILES);
    final Context context = new Context(shown, locked, new ArrayList<>());
    array(toml, "project.dependencies", DeclarationKind.DEPENDENCY, context);
    table(toml, "project.optional-dependencies", context);
    table(toml, "dependency-groups", context);
    array(toml, "build-system.requires", DeclarationKind.BUILD_REQUIREMENT, context);
    poetry(toml, "tool.poetry.dependencies", context);
    final TomlTable groups = toml.getTable("tool.poetry.group");
    if (groups != null) {
      groups
          .keySet()
          .forEach(group -> poetry(toml, "tool.poetry.group." + group + ".dependencies", context));
    }
    return new BuildModel(context.declarations, tools(toml, shown));
  }

  /** Where declarations go and what the file's neighbors say. */
  private record Context(String file, boolean locked, List<Declaration> declarations) {
    void add(
        final DeclarationKind kind,
        final String name,
        final String version,
        final String section,
        final int line) {
      declarations.add(
          new Declaration(
              BuildEcosystem.PYTHON,
              kind,
              name,
              version,
              section,
              "",
              file,
              line,
              locked ? Declaration.Flag.LOCKED : Declaration.Flag.NONE));
    }
  }

  private static List<ToolVersion> tools(final TomlParseResult toml, final String file) {
    final String required = toml.getString("tool.uv.required-version");
    final Matcher matcher =
        Pattern.compile("\\d+(\\.\\d+)*").matcher(required == null ? "" : required);
    if (!matcher.find()) {
      return List.of();
    }
    final int line = TomlLines.of(toml, List.of("tool", "uv", "required-version"));
    return List.of(new ToolVersion("uv", matcher.group(), file, line));
  }

  private static void table(final TomlParseResult toml, final String path, final Context context) {
    final TomlTable table = toml.getTable(path);
    if (table != null) {
      for (final String key : table.keySet()) {
        array(toml, path + "." + quote(key), DeclarationKind.DEPENDENCY, context);
      }
    }
  }

  private static String quote(final String key) {
    return key.matches("[A-Za-z0-9_-]+") ? key : "\"" + key + "\"";
  }

  private static void array(
      final TomlParseResult toml,
      final String path,
      final DeclarationKind kind,
      final Context context) {
    final TomlArray array = toml.isArray(path) ? toml.getArray(path) : null;
    if (array == null) {
      return;
    }
    for (int index = 0; index < array.size(); index++) {
      if (array.get(index) instanceof String) {
        requirement(array.getString(index), TomlLines.of(array, index), path, kind, context);
      }
    }
  }

  private static void requirement(
      final String text,
      final int line,
      final String section,
      final DeclarationKind kind,
      final Context context) {
    final Matcher matcher = REQUIREMENT.matcher(text);
    if (!matcher.matches() || text.contains(" @ ")) {
      return;
    }
    final String specifier = matcher.group(2).strip();
    context.add(kind, matcher.group(1), specifier.isEmpty() ? ANY : specifier, section, line);
  }

  private static void poetry(final TomlParseResult toml, final String path, final Context context) {
    final TomlTable table = toml.getTable(path);
    if (table == null) {
      return;
    }
    for (final String name : table.keySet()) {
      final Object value = table.get(List.of(name));
      final String version =
          value instanceof TomlTable details ? details.getString("version") : (String) value;
      if (!"python".equals(name) && version != null) {
        final String spec = PLAIN_VERSION.matcher(version).matches() ? "==" + version : version;
        context.add(
            DeclarationKind.DEPENDENCY, name, spec, path, TomlLines.of(table, List.of(name)));
      }
    }
  }

  private static TomlParseResult parse(final Path root, final String dir) {
    final Path file = BuildPaths.file(root, dir, FILE);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    final String shown = BuildPaths.relative(dir, FILE);
    LOGGER.log(Level.FINE, "event=build-policy-read-python file={0}", shown);
    try {
      final TomlParseResult toml = Toml.parse(file);
      if (toml.hasErrors()) {
        throw new BuildFileException(
            shown, "not valid TOML: " + toml.errors().getFirst().getMessage());
      }
      return toml;
    } catch (final IOException exception) {
      throw new BuildFileException(shown, "the file could not be read", exception);
    }
  }
}
