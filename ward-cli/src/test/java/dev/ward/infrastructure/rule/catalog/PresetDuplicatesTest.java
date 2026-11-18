package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A preset reports one place once: duplicates on the rule fixtures need an explicit reason. */
class PresetDuplicatesTest {
  private static final List<RuleDefinition> SHIPPED = CatalogRuleDefinitions.shipped();

  /** Fixtures the engine cannot yet run with every rule together (see the report of #599). */
  private static final Set<String> SKIPPED = Set.of();

  private static final int FUNCTION_LINE = 4;

  private static List<RuleDefinition> preset(
      final AnalysisLanguage language,
      final RulePreset preset
  ) {
    return SHIPPED.stream()
        .filter(rule -> rule.languages().equals(Set.of(language)))
        .filter(rule -> preset.selects(rule.presets()))
        .sorted(Comparator.comparing(RuleDefinition::id))
        .toList();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"JAVA", "KOTLIN", "TYPESCRIPT", "JAVASCRIPT", "PYTHON", "SWIFT"}
  )
  void noPresetReportsTheSamePlaceTwice(final AnalysisLanguage language) throws IOException {
    final Path root = RuleFixtureSet.root();
    final Set<String> found = new java.util.TreeSet<>();
    // strict selects every rule of standard, so a clean strict preset leaves standard clean too
    final List<RuleDefinition> rules = preset(language, RulePreset.STRICT);
    found.addAll(
        PresetDuplicates.pairs(
            rules,
            language,
            PresetDuplicates.fixtures(root, rules).stream()
                .filter(fixture -> !SKIPPED.contains(fixture.ruleId()))
                .toList()
        )
    );
    final String prefix = "ward:" + CatalogRow.key(language) + "/";
    final Set<String> kept = new java.util.TreeSet<>(KeptPairs.ALL.keySet());
    kept.removeIf(pair -> !pair.startsWith(prefix));
    final Set<String> unlisted = new java.util.TreeSet<>(found);
    unlisted.removeAll(kept);
    assertThat(unlisted).as("duplicate pairs of %s not in KeptPairs", language).isEmpty();
    assertThat(found).as("KeptPairs of %s that no longer duplicate", language).containsAll(kept);
  }

  @Test
  @DisplayName("a docstring is missing once")
  void aDocstringIsMissingOnce() throws IOException {
    // Given a Python function without a docstring
    final String source = "\"\"\"Module.\"\"\"\n\n\ndef run():\n    return 1\n";
    final List<RuleDefinition> rules = preset(AnalysisLanguage.PYTHON, RulePreset.STANDARD);

    // When the standard preset runs
    final List<String> reporting =
        new RuleEngine(BundledRuleMatchers.create())
            .run(
                List.of(new SourceUnit("run.py", AnalysisLanguage.PYTHON, source)),
                new RuleFixtureRunner().frontends(),
                rules
            )
            .findings().stream()
            .filter(finding -> finding.line() == FUNCTION_LINE)
            .filter(
                finding -> finding.ruleId().contains("docstring")
                    || finding.ruleId().contains("javadoc")
            )
            .map(AnalysisFinding::ruleId)
            .toList();

    // Then exactly one finding reports it
    assertThat(reporting).hasSize(1);
  }

  @Test
  @DisplayName("a new duplicate is caught")
  void aNewDuplicateIsCaught() throws IOException {
    // Given two rules of one preset that report the same place on the corpus fixtures
    final RuleDefinition original = SHIPPED.stream()
        .filter(rule -> "ward:python/missing-function-docstring".equals(rule.id()))
        .findFirst()
        .orElseThrow();
    final RuleDefinition twin = new RuleDefinition(
        "ward:python/twin-docstring",
        original.languages(),
        original.capability(),
        original.severity(),
        original.body()
    );
    final Path fixture =
        PresetDuplicates.fixtures(RuleFixtureSet.root(), List.of(original)).get(0).file();

    // When the duplicate test runs
    final Set<String> pairs =
        PresetDuplicates.pairs(
            List.of(original, twin),
            AnalysisLanguage.PYTHON,
            List.of(
                new PresetDuplicates.Fixture(original.id(), fixture),
                new PresetDuplicates.Fixture(twin.id(), fixture)
            )
        );

    // Then it fails and names both rules, unless the pair is in the explicit list
    assertThat(pairs)
        .containsExactly("ward:python/missing-function-docstring + ward:python/twin-docstring");
    assertThat(KeptPairs.ALL).doesNotContainKey(pairs.iterator().next());
  }

  @Test
  void everyDroppedRuleIsInNoPresetAndItsReplacementStays() throws IOException {
    final Map<String, RuleDefinition> byId = new java.util.HashMap<>();
    SHIPPED.forEach(rule -> byId.put(rule.id(), rule));
    final List<String> lines =
        java.nio.file.Files.readAllLines(
            RuleFixtureSet.root().resolveSibling("presets").resolve("duplicates-dropped.txt")
        );
    final List<String> problems = new java.util.ArrayList<>();
    for (final String line : lines) {
      if (line.startsWith("#") || line.isBlank()) {
        continue;
      }
      final String[] ids = line.split(" \\| ");
      final RuleDefinition dropped = byId.get(ids[0]);
      final RuleDefinition replacement = byId.get(ids[1]);
      if (dropped == null || replacement == null) {
        problems.add("unknown rule in " + line);
      } else if (!dropped.presets().isEmpty() || replacement.presets().isEmpty()) {
        problems.add("preset membership wrong in " + line);
      }
    }
    assertThat(problems).isEmpty();
  }
}
