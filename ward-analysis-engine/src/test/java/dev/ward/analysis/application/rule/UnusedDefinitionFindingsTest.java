package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks which definitions the whole-run unused rules report across files. */
final class UnusedDefinitionFindingsTest {

  private static final Span SPAN = new Span(3, 1, 4, 1, 10, 20);
  private static final String LIB_PY = "a/lib.py";
  private static final RuleDefinition FUNCTION = rule(UnusedDefinitionFindings.FUNCTION_ID);
  private static final RuleDefinition CLASS = rule(UnusedDefinitionFindings.CLASS_ID);
  private static final RuleDefinition SWIFT_DECLARATION =
      rule("ward:swift/unused-declaration", AnalysisLanguage.SWIFT);
  private static final RuleDefinition SWIFT_ASSIGN_ONLY =
      rule("ward:swift/assign-only-property", AnalysisLanguage.SWIFT);

  @Test
  void aDefinitionUsedByAnotherFileIsNotReported() {
    final Map<String, FileSummary> files =
        Map.of(
            LIB_PY,
            summary(LIB_PY, Set.of(), symbol("shared"), symbol("lonely")),
            "a/app.py",
            summary("a/app.py", Set.of("shared")));

    final List<AnalysisFinding> findings =
        UnusedDefinitionFindings.findings(List.of(FUNCTION), files);

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("lonely");
    assertThat(findings.get(0).file()).isEqualTo(LIB_PY);
  }

  @Test
  void exemptsDunderMainTestAndDiscoveredFiles() {
    final Map<String, FileSummary> files =
        Map.of(
            LIB_PY,
            summary(LIB_PY, Set.of(), symbol("__enter__"), symbol("main"), symbol("test_x")),
            "a/conftest.py",
            summary("a/conftest.py", Set.of(), symbol("fixture")),
            "a/test_lib.py",
            summary("a/test_lib.py", Set.of(), symbol("helper")));

    assertThat(UnusedDefinitionFindings.findings(List.of(FUNCTION), files)).isEmpty();
  }

  @Test
  void eachRuleReportsOnlyItsKind() {
    final Map<String, FileSummary> files =
        Map.of(LIB_PY, summary(LIB_PY, Set.of(), symbol("f"), new Symbol("K", SPAN, "class")));

    assertThat(UnusedDefinitionFindings.findings(List.of(CLASS), files))
        .extracting(AnalysisFinding::ruleId)
        .containsExactly(UnusedDefinitionFindings.CLASS_ID);
  }

  private static Symbol symbol(final String name) {
    return new Symbol(name, SPAN, "function");
  }

  private static FileSummary summary(
      final String path, final Set<String> used, final Symbol... declared) {
    return new FileSummary(path, List.of(), List.of(), List.of(declared), used);
  }

  private static RuleDefinition rule(final String id) {
    return rule(id, AnalysisLanguage.PYTHON);
  }

  private static RuleDefinition rule(final String id, final AnalysisLanguage language) {
    return new RuleDefinition(
        id,
        Set.of(language),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        "check = \"unused-definition\"");
  }

  @Test
  void aSwiftFunctionNoOtherFileUsesIsReported() {
    final Map<String, FileSummary> files =
        Map.of(
            "Sources/Lib.swift",
            summary("Sources/Lib.swift", Set.of(), symbol("shared"), symbol("lonely")),
            "Sources/App.swift",
            summary("Sources/App.swift", Set.of("shared")));

    assertThat(UnusedDefinitionFindings.findings(List.of(SWIFT_DECLARATION), files))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("lonely");
  }

  @Test
  void theSwiftRuleSkipsPythonFiles() {
    final Map<String, FileSummary> files =
        Map.of(LIB_PY, summary(LIB_PY, Set.of(), symbol("lonely")));

    assertThat(UnusedDefinitionFindings.findings(List.of(SWIFT_DECLARATION), files)).isEmpty();
  }

  @Test
  void aSwiftPropertyThatIsAssignedButNeverReadIsReported() {
    final FileSummary lib =
        new FileSummary(
            "Sources/Lib.swift",
            List.of(),
            List.of(),
            List.of(
                new Symbol("count", SPAN, "property"),
                new Symbol("label", SPAN, "property"),
                new Symbol("idle", SPAN, "property")),
            Set.of("label"),
            Set.of("count", "label"));

    assertThat(
            UnusedDefinitionFindings.findings(
                List.of(SWIFT_ASSIGN_ONLY), Map.of("Sources/Lib.swift", lib)))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .isEqualTo("property 'count' is assigned but never read in any file");
  }
}
