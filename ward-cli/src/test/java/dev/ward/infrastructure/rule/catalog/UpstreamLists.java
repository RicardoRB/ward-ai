package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.catalog.CatalogTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The pinned upstream rule lists and the out-of-scope files under {@code catalog/upstream}. A list
 * is {@code <tool>-<version>.txt}, one rule id per line followed by a tab and its group, with
 * {@code #} comment lines; its out-of-scope file is {@code <tool>-out-of-scope.toml}.
 */
final class UpstreamLists {

  private static final String DIRECTORY = "catalog/upstream";
  private static final Pattern LIST_FILE = Pattern.compile("(.+)-[0-9][0-9.]*\\.txt");
  private static final String SUFFIX = "-out-of-scope.toml";

  private UpstreamLists() {}

  /** Returns the pinned rule ids per tool, in file order. */
  static Map<String, Set<String>> pinned() {
    final Map<String, Set<String>> pinned = new LinkedHashMap<>();
    for (final Path file : files()) {
      final Matcher matcher = LIST_FILE.matcher(file.getFileName().toString());
      if (matcher.matches()) {
        pinned.put(matcher.group(1), ids(file));
      }
    }
    return pinned;
  }

  /** Returns the version the list of {@code tool} is pinned to, such as {@code 14.3.0}. */
  static String version(final String tool) {
    for (final Path file : files()) {
      final Matcher matcher = LIST_FILE.matcher(file.getFileName().toString());
      if (matcher.matches() && matcher.group(1).equals(tool)) {
        return file.getFileName().toString().substring(tool.length() + 1).replace(".txt", "");
      }
    }
    throw new IllegalStateException("no pinned list for " + tool);
  }

  /** Returns the lines of the list of {@code tool} that start with {@code #}. */
  static List<String> header(final String tool) {
    for (final Path file : files()) {
      if (file.getFileName().toString().startsWith(tool + "-")
          && file.getFileName().toString().endsWith(".txt")) {
        return lines(file).stream().filter(line -> line.startsWith("#")).toList();
      }
    }
    return List.of();
  }

  /** Returns the out-of-scope rules per tool, each with its reason. */
  static Map<String, Map<String, String>> outOfScope() {
    final Map<String, Map<String, String>> result = new LinkedHashMap<>();
    for (final Path file : files()) {
      final String name = file.getFileName().toString();
      if (name.endsWith(SUFFIX)) {
        result.put(name.substring(0, name.length() - SUFFIX.length()), reasons(file));
      }
    }
    return result;
  }

  /** Returns the tools of the catalog that have no pinned list. */
  static List<String> toolsWithoutList() {
    final Set<String> pinned = pinned().keySet();
    return CatalogTools.ALL.stream().filter(tool -> !pinned.contains(tool)).toList();
  }

  private static Map<String, String> reasons(final Path file) {
    final TomlParseResult toml = parse(file);
    final Map<String, String> reasons = new LinkedHashMap<>();
    final TomlArray groups = toml.getArray("group");
    for (int index = 0; groups != null && index < groups.size(); index++) {
      final TomlTable group = groups.getTable(index);
      final TomlArray rules = group.getArray("rules");
      for (int rule = 0; rules != null && rule < rules.size(); rule++) {
        reasons.put(rules.getString(rule), group.getString("reason"));
      }
    }
    return reasons;
  }

  private static TomlParseResult parse(final Path file) {
    try {
      final TomlParseResult toml = Toml.parse(file);
      if (toml.hasErrors()) {
        throw new IllegalStateException(file + " is not valid TOML: " + toml.errors().getFirst());
      }
      return toml;
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static Set<String> ids(final Path file) {
    final Set<String> ids = new LinkedHashSet<>();
    for (final String line : lines(file)) {
      if (!line.startsWith("#") && !line.isBlank()) {
        ids.add(line.split("\t", 2)[0]);
      }
    }
    return ids;
  }

  private static List<String> lines(final Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static List<Path> files() {
    final URL url = UpstreamLists.class.getResource("/" + DIRECTORY);
    if (url == null) {
      throw new IllegalStateException("missing resource directory " + DIRECTORY);
    }
    try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
      return files.sorted().toList();
    } catch (final IOException | URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
