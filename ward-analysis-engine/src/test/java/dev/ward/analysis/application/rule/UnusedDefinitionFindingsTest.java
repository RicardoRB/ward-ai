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
  private static final RuleDefinition FUNCTION = rule(UnusedDefinitionFindings.FUNCTION_ID);
  private static final RuleDefinition CLASS = rule(UnusedDefinitionFindings.CLASS_ID);

  @Test
  void aDefinitionUsedByAnotherFileIsNotReported() {
    final Map<String, FileSummary> files =
        Map.of(
            "a/lib.py", summary("a/lib.py", Set.of(), symbol("shared"), symbol("lonely")),
            "a/app.py", summary("a/app.py", Set.of("shared")));

    final List<AnalysisFinding> findings =
        UnusedDefinitionFindings.findings(List.of(FUNCTION), files);

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("lonely");
    assertThat(findings.get(0).file()).isEqualTo("a/lib.py");
  }

  @Test
  void exemptsDunderMainTestAndDiscoveredFiles() {
    final Map<String, FileSummary> files =
        Map.of(
            "a/lib.py",
                summary(
                    "a/lib.py", Set.of(), symbol("__enter__"), symbol("main"), symbol("test_x")),
            "a/conftest.py", summary("a/conftest.py", Set.of(), symbol("fixture")),
            "a/test_lib.py", summary("a/test_lib.py", Set.of(), symbol("helper")));

    assertThat(UnusedDefinitionFindings.findings(List.of(FUNCTION), files)).isEmpty();
  }

  @Test
  void eachRuleReportsOnlyItsKind() {
    final Map<String, FileSummary> files =
        Map.of(
            "a/lib.py", summary("a/lib.py", Set.of(), symbol("f"), new Symbol("K", SPAN, "class")));

    assertThat(UnusedDefinitionFindings.findings(List.of(CLASS), files))
        .extracting(AnalysisFinding::ruleId)
        .containsExactly(UnusedDefinitionFindings.CLASS_ID);
  }

  @Test
  void aMethodNamedByAnotherFileIsNotReported() {
    final RuleDefinition method = rule(UnusedDefinitionFindings.METHOD_ID);
    final Map<String, FileSummary> files =
        Map.of(
            "a/model.py",
                summary(
                    "a/model.py",
                    Set.of(),
                    new Symbol("save", SPAN, "method"),
                    new Symbol("drop", SPAN, "method")),
            "a/service.py", summary("a/service.py", Set.of("save")));

    assertThat(UnusedDefinitionFindings.findings(List.of(method), files))
        .extracting(AnalysisFinding::message)
        .singleElement()
        .asString()
        .contains("drop");
  }

  private static Symbol symbol(final String name) {
    return new Symbol(name, SPAN, "function");
  }

  private static FileSummary summary(
      final String path, final Set<String> used, final Symbol... declared) {
    return new FileSummary(path, List.of(), List.of(), List.of(declared), used);
  }

  private static RuleDefinition rule(final String id) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.PYTHON),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        "check = \"unused-definition\"");
  }
}
