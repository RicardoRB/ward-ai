package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleEngineDependencyViewTest {
  private static final Span AT_1 = new Span(1, 1, 1, 9, 0, 8);
  private static final String SYMBOLS_RULE = "ward:java/imports";
  private static final String SYNTAX_RULE = "ward:java/syntax";

  @Test
  void symbolsRuleSeesTheDependenciesOfItsUnit() {
    final SourceUnit unit = RuleEngineFixtures.unit("B.java");
    final DependencySnapshot snapshot = snapshot();

    final RuleRunResult result =
        new RuleEngine(new ViewReportingMatcher())
            .run(
                List.of(unit),
                List.of(java()),
                List.of(RuleEngineFixtures.rule(SYMBOLS_RULE, RuleCapability.SYMBOLS)),
                snapshot);

    assertThat(result.findings())
        .extracting(AnalysisFinding::message)
        .containsExactly("1 dependency");
  }

  @Test
  void syntaxRuleGetsNoView() {
    final RuleRunResult result =
        new RuleEngine(new ViewReportingMatcher())
            .run(
                List.of(RuleEngineFixtures.unit("B.java")),
                List.of(java()),
                List.of(RuleEngineFixtures.rule(SYNTAX_RULE, RuleCapability.SYNTAX)),
                snapshot());

    assertThat(result.findings())
        .extracting(AnalysisFinding::message)
        .containsExactly("0 dependency");
  }

  private static DependencySnapshot snapshot() {
    final FileSummary a = new FileSummary("A.java", List.of(), List.of(), List.of());
    final FileSummary b =
        new FileSummary("B.java", List.of(), List.of(new Reference("A", AT_1)), List.of());
    final DependencyGraph graph =
        new DependencyGraph(
            List.of(new DependencyEdge("B.java", "A.java", new Reference("A", AT_1))));
    return new DependencySnapshot(graph, Map.of("A.java", a, "B.java", b));
  }

  private static FrontendSupport java() {
    final LanguageFrontend frontend =
        unit -> new ParseResult(new SyntaxNode("file", AT_1, Map.of(), List.of()), List.of());
    return new FrontendSupport(
        AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS));
  }

  /** Reports how many dependencies the rule was given; the syntax-only path ignores the view. */
  private static final class ViewReportingMatcher implements RuleMatcher {
    @Override
    public CompiledRule compile(final RuleDefinition definition) {
      return new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return List.of(new RuleMatch(AT_1, "no view"));
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root, final DependencyView dependencies) {
          return List.of(new RuleMatch(AT_1, dependencies.dependencies().size() + " dependency"));
        }
      };
    }
  }
}
