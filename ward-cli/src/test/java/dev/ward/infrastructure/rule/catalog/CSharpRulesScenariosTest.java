package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
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

/** The scenarios of story #660: the C# rules mapped to the .NET analyzers and StyleCop. */
class CSharpRulesScenariosTest {
  private static final Set<String> DOTNET_TOOLS =
      Set.of("dotnet-analyzers", "dotnet-code-style", "stylecop");

  @Test
  @DisplayName("every upstream rule is classified")
  void everyUpstreamRuleIsClassified() {
    final Map<String, Set<String>> pinned = UpstreamLists.pinned();
    assertThat(pinned.keySet()).containsAll(DOTNET_TOOLS);

    final List<String> problems =
        UpstreamCoverage.problems(
            new RuleCatalogReader().load(),
            pinned,
            UpstreamLists.outOfScope()
        );

    assertThat(problems)
        .filteredOn(problem -> DOTNET_TOOLS.stream().anyMatch(problem::contains))
        .isEmpty();
  }

  @Test
  @DisplayName("a preset rule finds its violation")
  void aPresetRuleFindsItsViolation() throws IOException {
    final Map<String, RuleDefinition> rules =
        CatalogRuleDefinitions.all().stream()
            .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));
    final List<String> problems = new ArrayList<>();
    int checked = 0;
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.CSHARP)
          || row.presets().isEmpty()) {
        continue;
      }
      checked++;
      problems.addAll(check(rules.get(row.idFor(AnalysisLanguage.CSHARP)), row));
    }
    assertThat(checked).isPositive();
    assertThat(problems).isEmpty();
  }

  private static List<String> check(final RuleDefinition rule, final CatalogRow row)
      throws IOException {
    final RuleFixtureSet fixtures =
        RuleFixtureSet.of(RuleFixtureSet.root(), row, AnalysisLanguage.CSHARP);
    assertThat(fixtures.complete()).as("fixtures of %s", row.name()).isTrue();
    final RuleDefinition configured = RuleOptions.apply(rule, fixtures.options(""));
    final List<String> problems = new ArrayList<>();
    problems.addAll(run(configured, fixtures.violating(""), fixtures.expected("")));
    problems.addAll(run(configured, fixtures.passing(""), List.of()));
    return problems;
  }

  private static List<String> run(
      final RuleDefinition rule,
      final Path file,
      final List<String> expected
  ) throws IOException {
    final List<AnalysisFinding> findings =
        new RuleFixtureRunner()
            .run(
                rule,
                file.getFileName().toString(),
                Files.readString(file),
                FixtureTypeFacts.of(Optional.empty()),
                FixtureProjects.fixtureProject(file, rule)
            );
    return FixtureComparison.problems(
        rule.id(),
        AnalysisLanguage.CSHARP,
        file.getFileName().toString(),
        expected,
        findings
    );
  }
}
