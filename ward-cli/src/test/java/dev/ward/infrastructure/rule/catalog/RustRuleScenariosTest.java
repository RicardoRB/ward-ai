package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story R4 (#650): the Rust rules mapped to Clippy and the rustc lints. */
class RustRuleScenariosTest {
  private static final Set<String> RUST_TOOLS = Set.of("clippy", "rustc");
  private static final String RUST_DIRECTORY = "rust";
  private static final int MIN_CLIPPY_LINTS = 700;
  private static final int MIN_RUSTC_LINTS = 200;
  private static final int MIN_RULES = 50;

  @Test
  @DisplayName("every upstream rule is classified")
  void everyUpstreamRuleIsClassified() {
    final Map<String, Set<String>> pinned = only(UpstreamLists.pinned());
    final Map<String, Map<String, String>> outOfScope = only(UpstreamLists.outOfScope());

    final List<String> problems =
        UpstreamCoverage.problems(new RuleCatalogReader().load(), pinned, outOfScope);

    assertThat(pinned.keySet()).containsExactlyInAnyOrderElementsOf(RUST_TOOLS);
    assertThat(pinned.get("clippy")).hasSizeGreaterThan(MIN_CLIPPY_LINTS);
    assertThat(pinned.get("rustc")).hasSizeGreaterThan(MIN_RUSTC_LINTS);
    assertThat(problems).isEmpty();
  }

  @Test
  @DisplayName("a preset rule finds its violation")
  void aPresetRuleFindsItsViolation() throws IOException {
    final RuleCatalog catalog = new RuleCatalogReader().load();
    final Map<String, RuleDefinition> rules =
        CatalogRuleDefinitions.all().stream()
            .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final List<String> problems = new ArrayList<>();
    int checked = 0;
    for (final CatalogRow row : catalog.rows()) {
      if (row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.RUST)) {
        final String id = row.idFor(AnalysisLanguage.RUST);
        final RuleDefinition rule = rules.get(id);
        assertThat(rule.presets()).as("presets of %s", id).isNotEmpty();
        problems.addAll(fixtureProblems(runner, rule, id));
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(MIN_RULES);
    assertThat(problems).isEmpty();
  }

  private static List<String> fixtureProblems(
      final RuleFixtureRunner runner,
      final RuleDefinition rule,
      final String id
  ) throws IOException {
    final Path directory =
        RuleFixtureSet.root().resolve(RUST_DIRECTORY).resolve(id.substring(id.indexOf('/') + 1));
    final List<String> expected =
        Files.readAllLines(directory.resolve("expected.txt")).stream()
            .filter(line -> !line.isBlank())
            .toList();
    final List<String> problems = new ArrayList<>();
    problems.addAll(
        FixtureComparison.problems(
            id,
            AnalysisLanguage.RUST,
            "violating.rs",
            expected,
            findings(runner, rule, directory.resolve("violating.rs"))
        )
    );
    problems.addAll(
        FixtureComparison.problems(
            id,
            AnalysisLanguage.RUST,
            "passing.rs",
            List.of(),
            findings(runner, rule, directory.resolve("passing.rs"))
        )
    );
    return problems;
  }

  private static List<AnalysisFinding> findings(
      final RuleFixtureRunner runner,
      final RuleDefinition rule,
      final Path file
  ) throws IOException {
    return runner.run(
        rule,
        file.getFileName().toString(),
        Files.readString(file),
        FixtureTypeFacts.of(Optional.empty())
    );
  }

  private static <T> Map<String, T> only(final Map<String, T> byTool) {
    return byTool.entrySet().stream()
        .filter(entry -> RUST_TOOLS.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
