package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DependencyInvalidationTest {
  private static final String VERSION = "0.6.0";
  private static final String A = "A.kt";
  private static final String B = "B.kt";
  private static final String C = "C.kt";
  private static final String A_TEXT = "export N";
  private static final String B_TEXT = "import A";
  private static final String C_TEXT = "declare Q";

  private final Map<String, String> entries = new HashMap<>();
  private final AnalysisCache cache = AnalysisCacheFixtures.memory(entries);
  private final DependencyFixtureLanguage fixture = new DependencyFixtureLanguage();
  private final List<RuleDefinition> rules = List.of(DependencyFixtureLanguage.RULE);
  private final UnitResultCache results =
      new UnitResultCache(
          new RuleEngine(fixture.matcher()),
          AnalysisCacheFixtures.codec(),
          VERSION,
          fixture.dependencies());

  @Test
  void warmRunSummarizesAndAnalyzesNothing() {
    run(units(A_TEXT, B_TEXT, C_TEXT));
    fixture.parsed.clear();
    fixture.ruleRuns.clear();

    run(units(A_TEXT, B_TEXT, C_TEXT));

    assertThat(fixture.parsed).isEmpty();
    assertThat(fixture.ruleRuns).isEmpty();
  }

  @Test
  void corruptSummaryIsSummarizedAgain() {
    run(units(A_TEXT, B_TEXT, C_TEXT));
    entries.put(
        AnalysisCacheKey.summary(VERSION, DependencyFixtureLanguage.unit(A, A_TEXT)), "not json");
    fixture.parsed.clear();

    run(units(A_TEXT, B_TEXT, C_TEXT));

    assertThat(fixture.parsed).containsExactly(A);
  }

  @Test
  void exportChangeReanalyzesDirectDependentsOnly() {
    run(units(A_TEXT, B_TEXT, C_TEXT));
    fixture.ruleRuns.clear();

    run(units("export M", B_TEXT, C_TEXT));

    assertThat(fixture.ruleRuns).containsExactlyInAnyOrder(A, B);
  }

  @Test
  void bodyOnlyEditReanalyzesOnlyTheEditedFile() {
    run(units(A_TEXT, B_TEXT, C_TEXT));
    fixture.ruleRuns.clear();

    run(units(A_TEXT + "\n// body", B_TEXT, C_TEXT));

    assertThat(fixture.ruleRuns).containsExactly(A);
  }

  @Test
  void newImportReanalyzesTheImporter() {
    run(units(A_TEXT, B_TEXT, C_TEXT));
    fixture.ruleRuns.clear();

    run(units(A_TEXT, B_TEXT + "\nimport A", C_TEXT));

    assertThat(fixture.ruleRuns).contains(B);
    assertThat(fixture.ruleRuns).doesNotContain(C);
  }

  @Test
  void corruptIndexIsRewrittenAndReportEqualsColdRun() {
    final RuleRunResult cold = coldRun(units(A_TEXT, B_TEXT, C_TEXT));
    run(units(A_TEXT, B_TEXT, C_TEXT));
    entries.put(AnalysisCacheKey.index(), "{truncated");

    final RuleRunResult warm = run(units(A_TEXT, B_TEXT, C_TEXT));

    assertThat(warm.findings()).isEqualTo(cold.findings());
    assertThat(entries.get(AnalysisCacheKey.index())).contains("\"entries\"");
  }

  @Test
  void ruleSetWithoutSymbolsRuleKeepsResultKeys() {
    final List<RuleDefinition> syntaxOnly =
        List.of(
            new RuleDefinition(
                "ward:kotlin/length",
                DependencyFixtureLanguage.LANGUAGE,
                dev.ward.analysis.domain.rule.RuleCapability.SYNTAX,
                dev.ward.analysis.domain.ir.AnalysisSeverity.WARNING,
                "body"));
    final SourceUnit unit = DependencyFixtureLanguage.unit(A, A_TEXT);

    new UnitResultCache(
            new RuleEngine(fixture.matcher()),
            AnalysisCacheFixtures.codec(),
            VERSION,
            fixture.dependencies())
        .run(cache, List.of(unit), List.of(fixture.frontend()), syntaxOnly);

    assertThat(entries)
        .containsKey(AnalysisCacheKey.unit(AnalysisCacheKey.ruleSet(VERSION, syntaxOnly), unit));
  }

  private RuleRunResult run(final List<SourceUnit> units) {
    return results.run(cache, units, List.of(fixture.frontend()), rules);
  }

  private RuleRunResult coldRun(final List<SourceUnit> units) {
    return new UnitResultCache(
            new RuleEngine(fixture.matcher()),
            AnalysisCacheFixtures.codec(),
            VERSION,
            fixture.dependencies())
        .run(AnalysisCacheFixtures.NO_CACHE, units, List.of(fixture.frontend()), rules);
  }

  private static List<SourceUnit> units(final String a, final String b, final String c) {
    return List.of(
        DependencyFixtureLanguage.unit(A, a),
        DependencyFixtureLanguage.unit(B, b),
        DependencyFixtureLanguage.unit(C, c));
  }
}
