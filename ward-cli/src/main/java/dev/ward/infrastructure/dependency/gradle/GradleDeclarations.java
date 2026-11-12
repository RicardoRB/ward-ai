package dev.ward.infrastructure.dependency.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Finds which {@code group:name} a module's build script declares, by literal string notation, map
 * notation or version-catalog alias.
 *
 * <p>shortcut: a dependency declared through a function, a convention plugin or a platform looks
 * transitive here; Gradle's lockfile records no declarations, so only literal notations and catalog
 * aliases in the module's own script are direct.
 */
final class GradleDeclarations {
  private static final Pattern COORDINATE =
      Pattern.compile("[\"']([\\w.\\-]+):([\\w.\\-]+)(?::[^\"'\\s]*)?[\"']");
  private static final Pattern MAP_NOTATION =
      Pattern.compile(
          "group\\s*[:=]\\s*[\"']([^\"']+)[\"']\\s*,\\s*name\\s*[:=]\\s*[\"']([^\"']+)[\"']");
  private static final Pattern ALIAS = Pattern.compile("\\blibs\\.([A-Za-z0-9_.]+)");

  private final Map<String, String> catalog;

  /** Reads the version catalog of {@code root}, if it has one. */
  GradleDeclarations(final Path root) {
    this.catalog = catalog(root);
  }

  /** Returns the {@code group:name} that the script {@code script} declares. */
  Set<String> declared(final Path script) {
    final Set<String> names = new LinkedHashSet<>();
    final String text;
    try {
      text = Files.readString(script);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    COORDINATE.matcher(text).results().forEach(m -> names.add(m.group(1) + ":" + m.group(2)));
    MAP_NOTATION.matcher(text).results().forEach(m -> names.add(m.group(1) + ":" + m.group(2)));
    final Matcher aliases = ALIAS.matcher(text);
    while (aliases.find()) {
      resolveAlias(aliases.group(1)).ifPresent(names::add);
    }
    return names;
  }

  /** Finds the catalog entry of {@code libs.<alias>}, trying shorter prefixes for accessors. */
  private Optional<String> resolveAlias(final String alias) {
    String candidate = alias;
    while (!candidate.isEmpty()) {
      final String found = catalog.get(candidate);
      if (found != null) {
        return Optional.of(found);
      }
      final int dot = candidate.lastIndexOf('.');
      candidate = dot < 0 ? "" : candidate.substring(0, dot);
    }
    return Optional.empty();
  }

  /** Returns the catalog libraries by dotted alias, with the {@code group:name} each stands for. */
  private static Map<String, String> catalog(final Path root) {
    final Map<String, String> libraries = new LinkedHashMap<>();
    final Path file = root.resolve("gradle").resolve("libs.versions.toml");
    if (!Files.isRegularFile(file)) {
      return libraries;
    }
    try {
      final TomlParseResult parsed = Toml.parse(Files.readString(file));
      final TomlTable table = parsed.getTable("libraries");
      if (!parsed.hasErrors() && table != null) {
        table
            .keySet()
            .forEach(
                key ->
                    module(table, key)
                        .ifPresent(name -> libraries.put(key.replaceAll("[-_]", "."), name)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return libraries;
  }

  private static Optional<String> module(final TomlTable libraries, final String key) {
    final String text = libraries.getString(key);
    if (text != null) {
      final String[] parts = text.split(":");
      return parts.length >= 2 ? Optional.of(parts[0] + ":" + parts[1]) : Optional.empty();
    }
    final TomlTable table = libraries.getTable(key);
    if (table == null) {
      return Optional.empty();
    }
    final String module = table.getString("module");
    if (module != null) {
      return Optional.of(module);
    }
    final String group = table.getString("group");
    final String name = table.getString("name");
    return group != null && name != null ? Optional.of(group + ":" + name) : Optional.empty();
  }
}
