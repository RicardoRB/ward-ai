package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A unit whose frontend reports syntax errors is an execution error, never checked. */
final class RuleEngineDiagnosticsTest {

  @Test
  void unitWithSyntaxErrorsIsNotCheckedAndIsReportedAsAnError() {
    final SyntaxNode tree =
        new SyntaxNode("file_input", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());
    final FrontendSupport frontend =
        new FrontendSupport(
            AnalysisLanguage.PYTHON,
            unit -> new ParseResult(tree, List.of(diagnostic())),
            Set.of(RuleCapability.SYNTAX));
    final RuleMatcher matcher = definition -> new Everything(definition);
    final RuleDefinition rule =
        new RuleDefinition(
            "ward:python/test",
            AnalysisLanguage.PYTHON,
            RuleCapability.SYNTAX,
            AnalysisSeverity.ERROR,
            "body");

    final RuleRunResult result =
        new RuleEngine(matcher)
            .run(
                List.of(new SourceUnit("broken.py", AnalysisLanguage.PYTHON, "def (:\n")),
                List.of(frontend),
                List.of(rule));

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).reason()).isEqualTo("the unit has syntax errors");
  }

  private static AnalysisFinding diagnostic() {
    return new AnalysisFinding(
        "ward:python/syntax",
        AnalysisSeverity.ERROR,
        "syntax error",
        "broken.py",
        1,
        1,
        null,
        List.of());
  }

  /** A rule that reports one match on every tree it sees. */
  private record Everything(RuleDefinition definition) implements CompiledRule {
    @Override
    public List<RuleMatch> matches(final SyntaxNode root) {
      return List.of(new RuleMatch(null, "seen"));
    }
  }
}
