package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Proves that the repository's {@code ward-rules.toml} selects a Ward rule for every rule of the
 * Checkstyle module set, the PMD ruleset and the standard SpotBugs rows, as the catalog maps them
 * (#347 part B), and that one seeded violation per upstream tool fails it. The catalog and the
 * upstream listings are read from the test resources; the configs are read from the repository
 * root. The tests run from the ward-cli module.
 */
class RepositoryWardRulesTest {
  /** The repository root, one level above the ward-cli module. */
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  private static final Path CATALOG = ROOT.resolve("ward-cli/src/test/resources/catalog");
  private static final Path STATUS = CATALOG.resolve("status/java");
  private static final Path PMD_UPSTREAM = CATALOG.resolve("upstream/pmd-7.29.0.txt");
  private static final Path RULES_FILE = ROOT.resolve("ward-rules.toml");
  private static final Path SEEDED =
      ROOT.resolve("ward-cli/src/test/resources/fixtures/repository-ward-rules/Seeded.java");

  private static final String IMPLEMENTED = "implemented";
  private static final String STANDARD = "standard";
  private static final String JAVA = "java";
  private static final String OUT_OF_SCOPE_SUFFIX = "out-of-scope.toml";
  private static final String WARD_OWN = "ward-own.toml";

  /** The Checkstyle modules that are containers, not checks. */
  private static final Set<String> CHECKSTYLE_CONTAINERS = Set.of("Checker", "TreeWalker");

  private static final Pattern CHECKSTYLE_MODULE = Pattern.compile("<module name=\"([^\"]+)\"");
  private static final Pattern PMD_GROUP_REF =
      Pattern.compile("<rule ref=\"category/java/([a-z]+)\\.xml\"");
  private static final Pattern PMD_EXCLUDE = Pattern.compile("<exclude name=\"([^\"]+)\"");
  private static final Pattern PMD_TUNED_REF =
      Pattern.compile("<rule ref=\"category/java/[a-z]+\\.xml/([A-Za-z]+)\"");

  /**
   * Mapped PMD rules that Ward does not implement yet (their catalog rows are planned), so the
   * repository cannot select them. Each one is still enforced by the PMD ruleset until its issue
   * lands; the test fails when this list changes, so the gap is reviewed, not silently widened.
   */
  private static final Set<String> PLANNED_PMD_GAPS =
      Set.of(
          "ward:java/avoid-no-argument-super-constructor-call",
          "ward:java/simplify-boolean-expression",
          "ward:java/unnecessary-parentheses",
          "ward:java/unused-private-field",
          "ward:java/final-parameters");

  /**
   * Mapped rules that the repository deliberately leaves out. PMD added
   * LocalVariableDeclarationShouldBeAtStartOfBlock in 7.29.0; the Maven build still runs PMD 7.17.0
   * (maven-pmd-plugin 3.28.0), which never enforced it, and the tree has about 17,000 sites that
   * declare a local with an initializer in mid-block.
   */
  private static final Set<String> NOT_ENFORCED_BY_PMD_IN_BUILD =
      Set.of("ward:java/local-variable-declaration-should-be-at-start-of-block");

  private static final String SEEDED_CHECKSTYLE = "ward:java/magic-number";
  private static final String SEEDED_PMD = "ward:java/unnecessary-return";
  private static final String SEEDED_SPOTBUGS = "ward:java/st-write-to-static-from-instance-method";

  @TempDir Path dir;

  /** A catalog row for Java, with the status of its Java row. */
  private record Row(
      String wardId,
      String status,
      List<String> checkstyle,
      List<String> pmd,
      List<String> presets,
      boolean spotbugs) {}

  @Test
  void everyMappedPmdAndSpotbugsRuleIsIncluded() throws IOException {
    final Set<String> include = include();
    final Map<String, String> notImplemented = new HashMap<>();
    for (final Row row : mappedRows()) {
      if (NOT_ENFORCED_BY_PMD_IN_BUILD.contains(row.wardId())) {
        assertThat(include).doesNotContain(row.wardId());
      } else if (IMPLEMENTED.equals(row.status())) {
        assertThat(include).as("mapped rule %s", row.wardId()).contains(row.wardId());
      } else {
        notImplemented.put(row.wardId(), row.status());
      }
    }
    assertThat(notImplemented.keySet())
        .as("mapped rules Ward does not implement yet")
        .containsExactlyInAnyOrderElementsOf(PLANNED_PMD_GAPS);
  }

  @Test
  void everyCheckstyleModuleHasAnIncludedRule() throws IOException {
    final Set<String> include = include();
    final List<Row> rows = mappedRows();
    for (final String module : checkstyleModules()) {
      assertThat(rows.stream().filter(row -> row.checkstyle().contains(module)))
          .as("checkstyle module %s", module)
          .anySatisfy(row -> assertThat(include).contains(row.wardId()));
    }
  }

  @Test
  void includesEverySelectedRuleAndOnlyJava() throws IOException {
    final TomlParseResult rules = Toml.parse(Files.readString(RULES_FILE));
    final TomlTable select = rules.getTable("select");
    assertThat(select.getString("preset")).isEqualTo("none");
    assertThat(strings(select, "languages")).containsExactly(JAVA);
    assertThat(include()).allSatisfy(id -> assertThat(id).startsWith("ward:java/"));
    assertThat(Files.readString(RULES_FILE)).doesNotContain("[rules.\"ward:");
  }

  @Test
  void seededViolationsFailTheRepositoryRules() throws IOException {
    final FixtureProject fixture = new FixtureProject(dir);
    fixture.config(Files.readString(RULES_FILE));
    Files.createDirectories(dir.resolve("src"));
    Files.copy(SEEDED, dir.resolve("src/Seeded.java"));

    assertThat(FixtureProject.ids(fixture.run(List.of(), null, new HashMap<>())))
        .contains(SEEDED_CHECKSTYLE, SEEDED_PMD, SEEDED_SPOTBUGS);
  }

  private static Set<String> include() throws IOException {
    final TomlTable select = Toml.parse(Files.readString(RULES_FILE)).getTable("select");
    return strings(select, "include").stream()
        .map(name -> "ward:" + name.replaceFirst("\\.", "/"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<String> checkstyleModules() throws IOException {
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
  private static List<Row> mappedRows() throws IOException {
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
      for (final Path file :
          files.filter(RepositoryWardRulesTest::isCatalogFile).sorted().toList()) {
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
    return name.endsWith(".toml") && !name.equals(WARD_OWN) && !name.endsWith(OUT_OF_SCOPE_SUFFIX);
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

  private static List<String> strings(final TomlTable table, final String key) {
    final TomlArray array = table.getArray(key);
    return array == null ? List.of() : array.toList().stream().map(String::valueOf).toList();
  }
}
