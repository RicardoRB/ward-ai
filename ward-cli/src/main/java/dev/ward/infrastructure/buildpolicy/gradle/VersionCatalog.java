package dev.ward.infrastructure.buildpolicy.gradle;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.infrastructure.buildpolicy.TomlLines;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads {@code gradle/libs.versions.toml}: its libraries and plugins with their versions. */
final class VersionCatalog {
  private static final String VERSIONS = "versions";
  private static final String VERSION = "version";
  private static final String CATALOG = "catalog";

  private final TomlParseResult toml;
  private final String file;
  private final boolean locked;
  private final List<Declaration> out = new ArrayList<>();

  private VersionCatalog(final TomlParseResult toml, final String file, final boolean locked) {
    this.toml = toml;
    this.file = file;
    this.locked = locked;
  }

  /**
   * Reads the catalog {@code path}, named {@code file} in findings and errors.
   *
   * @throws BuildFileException if the file is unreadable or not valid TOML
   */
  static List<Declaration> read(final Path path, final String file, final boolean locked) {
    final TomlParseResult toml;
    try {
      toml = Toml.parse(path);
    } catch (final IOException exception) {
      throw new BuildFileException(file, "the file could not be read", exception);
    }
    if (toml.hasErrors()) {
      throw new BuildFileException(
          file, "not valid TOML: " + toml.errors().getFirst().getMessage());
    }
    final VersionCatalog catalog = new VersionCatalog(toml, file, locked);
    catalog.entries(toml.getTable("libraries"), DeclarationKind.DEPENDENCY);
    catalog.entries(toml.getTable("plugins"), DeclarationKind.PLUGIN);
    return catalog.out;
  }

  private void entries(final TomlTable table, final DeclarationKind kind) {
    if (table == null) {
      return;
    }
    for (final String key : table.keySet()) {
      final int line = TomlLines.of(table, List.of(key));
      final Object entry = table.get(List.of(key));
      if (entry == null) {
        continue;
      }
      final String coordinate = coordinate(table, key, entry, kind);
      final Version version = version(entry);
      out.add(
          new Declaration(
              BuildEcosystem.GRADLE,
              kind,
              coordinate,
              version.text(),
              CATALOG,
              "",
              file,
              line,
              flag(version, kind == DeclarationKind.DEPENDENCY && locked)));
    }
  }

  private Declaration.Flag flag(final Version version, final boolean pinnable) {
    if (version.undeterminable()) {
      return Declaration.Flag.UNDETERMINABLE;
    }
    return pinnable ? Declaration.Flag.LOCKED : Declaration.Flag.NONE;
  }

  /** The version of an entry; {@code text} is null when it has none or cannot be read. */
  private record Version(String text, boolean undeterminable) {}

  private static String coordinate(
      final TomlTable table, final String key, final Object entry, final DeclarationKind kind) {
    if (entry instanceof String text) {
      final List<String> parts = List.of(text.split(":", -1));
      return kind == DeclarationKind.PLUGIN || parts.size() < 2
          ? parts.getFirst()
          : parts.get(0) + ":" + parts.get(1);
    }
    final TomlTable details = table.getTable(List.of(key));
    if (details == null) {
      return key;
    }
    if (details.getString("module") != null) {
      return details.getString("module");
    }
    if (details.getString("id") != null) {
      return details.getString("id");
    }
    return details.getString("group") + ":" + details.getString("name");
  }

  private Version version(final Object entry) {
    if (entry instanceof String text) {
      final String[] parts = text.split(":", -1);
      return parts.length > 2 ? literal(parts[2]) : new Version(null, false);
    }
    final TomlTable details = (TomlTable) entry;
    if (details.isString(VERSION)) {
      return literal(details.getString(VERSION));
    }
    final String reference = details.getString("version.ref");
    if (reference != null) {
      return toml.isString(List.of(VERSIONS, reference))
          ? literal(toml.getString(List.of(VERSIONS, reference)))
          : new Version(null, true);
    }
    return new Version(null, false);
  }

  private static Version literal(final String text) {
    return new Version(text, false);
  }
}
