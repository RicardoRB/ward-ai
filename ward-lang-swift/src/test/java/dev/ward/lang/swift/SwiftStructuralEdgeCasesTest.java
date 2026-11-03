package dev.ward.lang.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases of the Swift rules: tree shapes that the fixtures do not isolate. */
final class SwiftStructuralEdgeCasesTest {

  private static final String FIXTURES = "fixtures/swift-structural/edge/";

  @Test
  void complexityCountsElseIfGuardSwitchCasesTernaryAndLogicalOperators() throws Exception {
    // three ifs (one an else if), guard, for, while, repeat, two cases (default is not one), ?:,
    // &&, ||, catch
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run("complexity", SwiftRuleRuns.read(FIXTURES + "decisions.swift"))
            .findings();

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .containsExactly("function decisions has complexity 14, the limit is 10");
  }

  @Test
  void closureDecisionsDoNotCountTowardTheEnclosingFunction() throws Exception {
    // Nine ifs in the function make 10; the closure's three ifs must not push it over the limit.
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run(
                "complexity", SwiftRuleRuns.read(FIXTURES + "closure-outside-function.swift"))
            .findings();

    assertThat(findings).isEmpty();
  }

  @Test
  void nestedTypeLinesCountTowardTheOuterTypeWithinTheLimit() throws Exception {
    final String source = SwiftRuleRuns.read(FIXTURES + "nested-type.swift");

    assertThat(SwiftRuleRuns.run("type-body-length", source).findings()).isEmpty();
  }

  @Test
  void initializerWithDefaultValuesCountsEachParameter() throws Exception {
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run(
                "parameter-count", SwiftRuleRuns.read(FIXTURES + "initializer-defaults.swift"))
            .findings();

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .containsExactly("function init has 6 parameters, the limit is 5");
  }

  @Test
  void elseClauseAddsNoDecisionOfItsOwn() throws Exception {
    // Nine ifs make 10; nine else clauses would make 19 if they counted.
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run("complexity", SwiftRuleRuns.read(FIXTURES + "else-only.swift"))
            .findings();

    assertThat(findings).isEmpty();
  }

  @Test
  void backtickedAndUnderscoredNamesAreCheckedWithoutTheirDecoration() throws Exception {
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run("naming", SwiftRuleRuns.read(FIXTURES + "decorated-names.swift"))
            .findings();

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .containsExactly("`load_config` should be lowerCamelCase");
  }
}
