package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
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
  private static final Path ROOT = RepositoryRuleMapping.ROOT;

  private static final Path RULES_FILE = ROOT.resolve("ward-rules.toml");
  private static final Path SEEDED =
      ROOT.resolve("ward-cli/src/test/resources/fixtures/repository-ward-rules/Seeded.java");

  private static final String IMPLEMENTED = "implemented";
  private static final String JAVA = "java";

  /** The Checkstyle modules that are containers, not checks. */

  /**
   * Mapped PMD rules that Ward does not implement yet (their catalog rows are planned), so the
   * repository cannot select them. Each one is still enforced by the PMD ruleset until its issue
   * lands; the test fails when this list changes, so the gap is reviewed, not silently widened.
   */
  private static final Set<String> PLANNED_PMD_GAPS = Set.of();

  /**
   * Mapped rules that the repository deliberately leaves out. PMD added
   * LocalVariableDeclarationShouldBeAtStartOfBlock in 7.29.0; the Maven build still runs PMD 7.17.0
   * (maven-pmd-plugin 3.28.0), which never enforced it, and the tree has about 17,000 sites that
   * declare a local with an initializer in mid-block.
   */
  private static final Set<String> NOT_ENFORCED_BY_PMD_IN_BUILD =
      Set.of("ward:java/local-variable-declaration-should-be-at-start-of-block");

  /** The {@code build-policy} rules share the file and are not Java rules. */
  private static final String BUILD_RULES = "build.";

  private static final String SEEDED_CHECKSTYLE = "ward:java/magic-number";
  private static final String SEEDED_PMD = "ward:java/unnecessary-return";
  private static final String SEEDED_SPOTBUGS = "ward:java/st-write-to-static-from-instance-method";

  @TempDir Path dir;

  @Test
  void everyMappedPmdAndSpotbugsRuleIsIncluded() throws IOException {
    final Set<String> include = include();
    final Map<String, String> notImplemented = new HashMap<>();
    for (final RepositoryRuleMapping.Row row : RepositoryRuleMapping.mappedRows()) {
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
    final List<RepositoryRuleMapping.Row> rows = RepositoryRuleMapping.mappedRows();
    for (final String module : RepositoryRuleMapping.checkstyleModules()) {
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
    assertThat(RepositoryRuleMapping.strings(select, "languages")).containsExactly(JAVA);
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
    return RepositoryRuleMapping.strings(select, "include").stream()
        .filter(name -> !name.startsWith(BUILD_RULES))
        .map(name -> "ward:" + name.replaceFirst("\\.", "/"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
