package dev.ward.infrastructure.buildpolicy.cargo;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
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
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads {@code Cargo.toml}: the dependency tables, the workspace ones and the target ones. */
public final class CargoBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(CargoBuildReader.class.getName());
  private static final String FILE = "Cargo.toml";
  private static final List<String> LOCKFILES = List.of("Cargo.lock");
  private static final List<String> TABLES =
      List.of("dependencies", "dev-dependencies", "build-dependencies");

  @Override
  public List<String> modules(final Path root, final String dir) {
    final TomlParseResult toml = parse(root, dir);
    final TomlArray members = toml == null ? null : toml.getArray("workspace.members");
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
    final Declaration.Flag flag =
        BuildPaths.anyFile(root, dir, LOCKFILES) ? Declaration.Flag.LOCKED : Declaration.Flag.NONE;
    final List<Declaration> declarations = new ArrayList<>();
    final List<String> prefixes = new ArrayList<>(List.of(""));
    prefixes.add("workspace.");
    final TomlTable targets = toml.getTable("target");
    if (targets != null) {
      targets.keySet().forEach(target -> prefixes.add("target.\"" + target + "\"."));
    }
    for (final String prefix : prefixes) {
      for (final String table : TABLES) {
        final String path = prefix + table;
        read(toml.getTable(path), path, new Source(shown, flag), declarations);
      }
    }
    return new BuildModel(declarations, List.of());
  }

  private record Source(String file, Declaration.Flag flag) {}

  private static void read(
      final TomlTable table,
      final String path,
      final Source source,
      final List<Declaration> declarations
  ) {
    if (table == null) {
      return;
    }
    for (final String key : table.keySet()) {
      final Object value = table.get(List.of(key));
      final String version = version(value);
      if (version != null) {
        declarations.add(
            new Declaration(
                BuildEcosystem.CARGO,
                DeclarationKind.DEPENDENCY,
                key,
                version,
                path,
                crate(key, value),
                source.file(),
                TomlLines.of(table, List.of(key)),
                source.flag()
            )
        );
      }
    }
  }

  private static String version(final Object value) {
    if (value instanceof String text) {
      return text;
    }
    return value instanceof TomlTable details ? details.getString("version") : null;
  }

  /** A renamed dependency is the crate its {@code package} key names. */
  private static String crate(final String key, final Object value) {
    final String renamed = value instanceof TomlTable details ? details.getString("package") : null;
    return renamed == null ? key : renamed;
  }

  private static TomlParseResult parse(final Path root, final String dir) {
    final Path file = BuildPaths.file(root, dir, FILE);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    final String shown = BuildPaths.relative(dir, FILE);
    LOGGER.log(Level.FINE, "event=build-policy-read-cargo file={0}", shown);
    try {
      final TomlParseResult toml = Toml.parse(file);
      if (toml.hasErrors()) {
        throw new BuildFileException(
            shown,
            "not valid TOML: " + toml.errors().getFirst().getMessage()
        );
      }
      return toml;
    } catch (final IOException exception) {
      throw new BuildFileException(shown, "the file could not be read", exception);
    }
  }
}
