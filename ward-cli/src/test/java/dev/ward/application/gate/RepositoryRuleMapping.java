package dev.ward.application.gate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

/** Reads which catalog rows this repository's {@code config/} files enforce. */
final class RepositoryRuleMapping {

  static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
  static final String JAVA = "java";
  private static final Path CATALOG = ROOT.resolve("ward-cli/src/test/resources/catalog");
  private static final Path STATUS = CATALOG.resolve("status/java");
  private static final Path PMD_UPSTREAM = CATALOG.resolve("upstream/pmd-7.29.0.txt");
  private static final String STANDARD = "standard";
  private static final String OUT_OF_SCOPE_SUFFIX = "out-of-scope.toml";
  private static final String WARD_OWN = "ward-own.toml";
  private static final Set<String> CHECKSTYLE_CONTAINERS = Set.of("Checker", "TreeWalker");
  private static final Pattern CHECKSTYLE_MODULE = Pattern.compile("<module name=\"([^\"]+)\"");
  private static final Pattern PMD_GROUP_REF =
      Pattern.compile("<rule ref=\"category/java/([a-z]+)\\.xml\"");
  private static final Pattern PMD_EXCLUDE = Pattern.compile("<exclude name=\"([^\"]+)\"");
  private static final Pattern PMD_TUNED_REF =
      Pattern.compile("<rule ref=\"category/java/[a-z]+\\.xml/([A-Za-z]+)\"");

  private RepositoryRuleMapping() {}

  /** A catalog row for Java, with the status of its Java row. */
  record Row(
      String wardId,
      String status,
      List<String> checkstyle,
      List<String> pmd,
      List<String> presets,
      boolean spotbugs) {}

  static Set<String> checkstyleModules() throws IOException {
    final Matcher matcher =
        CHECKSTYLE_MODULE.matcher(Files.readString(ROOT.resolve("config/checkstyle.xml")));
    final Set<String> modules = new HashSet<>();
    while (matcher.find()) {
      if (!CHECKSTYLE_CONTAINERS.contains(matcher.group(1))) {
        modules.add(matcher.group(1));
      }
    }
    return modules;
  }

  /**
   * The Java rows of the catalog that map to a PMD group of the ruleset, SpotBugs or Checkstyle.
   */
  static List<Row> mappedRows() throws IOException {
    final Set<String> checkstyle = checkstyleModules();
    final String pmdXml = Files.readString(ROOT.resolve("config/pmd-ruleset.xml"));
    final Set<String> groups = matches(PMD_GROUP_REF, pmdXml);
    final Set<String> excluded = matches(PMD_EXCLUDE, pmdXml);
    excluded.removeAll(matches(PMD_TUNED_REF, pmdXml));
    final Map<String, String> pmdGroup = pmdGroups();
    final List<Row> mapped = new ArrayList<>();
    for (final Row row : catalogRows()) {
      final boolean viaCheckstyle = row.checkstyle().stream().anyMatch(checkstyle::contains);
      final boolean viaPmd =
          row.pmd().stream()
              .anyMatch(rule -> groups.contains(pmdGroup.get(rule)) && !excluded.contains(rule));
      final boolean viaSpotbugs = row.spotbugs() && row.presets().contains(STANDARD);
      if (viaCheckstyle || viaPmd || viaSpotbugs) {
        mapped.add(row);
      }
    }
    return mapped;
  }

  private static List<Row> catalogRows() throws IOException {
    final List<Row> rows = new ArrayList<>();
    try (Stream<Path> files = Files.list(CATALOG)) {
      for (final Path file : files.filter(RepositoryRuleMapping::isCatalogFile).sorted().toList()) {
        final TomlParseResult catalog = Toml.parse(Files.readString(file));
        final TomlTable statuses = javaStatuses(file.getFileName().toString());
        final TomlArray table = catalog.getArray("rule");
        for (int index = 0; table != null && index < table.size(); index++) {
          rows.add(row(table.getTable(index), statuses));
        }
      }
    }
    return rows;
  }

  private static boolean isCatalogFile(final Path file) {
    final String name = file.getFileName().toString();
    return name.endsWith(".toml") && !WARD_OWN.equals(name) && !name.endsWith(OUT_OF_SCOPE_SUFFIX);
  }

  private static TomlTable javaStatuses(final String catalogFile) throws IOException {
    final Path file = STATUS.resolve(catalogFile);
    return Files.exists(file) ? Toml.parse(Files.readString(file)) : null;
  }

  private static Row row(final TomlTable rule, final TomlTable statuses) {
    final String name = rule.getString("name");
    final TomlTable ids = rule.getTable("ids");
    final String java = ids == null ? null : ids.getString(JAVA);
    final String wardId = java == null ? "ward:java/" + name : java;
    return new Row(
        wardId,
        statusOf(name, statuses),
        strings(rule, "checkstyle"),
        strings(rule, "pmd"),
        strings(rule, "presets"),
        rule.get("spotbugs") != null);
  }

  private static String statusOf(final String name, final TomlTable statuses) {
    final Object value = statuses == null ? null : statuses.get(name);
    if (value instanceof TomlTable table) {
      return table.getString("status");
    }
    return value == null ? "missing" : value.toString();
  }

  private static Map<String, String> pmdGroups() throws IOException {
    final Map<String, String> groups = new HashMap<>();
    for (final String line : Files.readAllLines(PMD_UPSTREAM)) {
      if (!line.startsWith("#") && !line.isBlank()) {
        final String[] columns = line.split("\t");
        groups.put(columns[0], columns[1]);
      }
    }
    return groups;
  }

  private static Set<String> matches(final Pattern pattern, final String text) {
    final Matcher matcher = pattern.matcher(text);
    final Set<String> found = new HashSet<>();
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    return found;
  }

  static List<String> strings(final TomlTable table, final String key) {
    final TomlArray array = table.getArray(key);
    return array == null ? List.of() : array.toList().stream().map(String::valueOf).toList();
  }
}
