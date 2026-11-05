package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.GraphFixtures;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Lazy and shared control-flow lowering in the rule engine. */
class RuleEngineFlowTest {

  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void aRunWithoutFlowRulesLowersNothing() {
    final CountingLowering lowering = new CountingLowering(List.of(), List.of());
    final RuleRunResult result =
        run(lowering, List.of(rule("ward:java/syntax", RuleCapability.SYNTAX)));

    assertThat(lowering.calls()).isZero();
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void severalFlowRulesShareOneLoweringOfTheUnit() {
    final CountingLowering lowering = new CountingLowering(List.of(graph("a")), List.of());
    final RuleRunResult result =
        run(
            lowering,
            List.of(
                rule("ward:java/flow-one", RuleCapability.DATAFLOW),
                rule("ward:java/flow-two", RuleCapability.DATAFLOW)));

    assertThat(lowering.calls()).isEqualTo(1);
    assertThat(result.findings()).hasSize(2);
  }

  @Test
  void aBodyThatFailsToLowerDoesNotStopItsSiblings() {
    final AnalysisFinding failure =
        new AnalysisFinding(
            "control-flow",
            AnalysisSeverity.WARNING,
            "control flow not lowered for broken",
            "src/X.java",
            1,
            1,
            null);
    final CountingLowering lowering =
        new CountingLowering(List.of(graph("fine")), List.of(failure));
    final RuleRunResult result =
        run(lowering, List.of(rule("ward:java/flow", RuleCapability.DATAFLOW)));

    assertThat(result.findings())
        .extracting(AnalysisFinding::message)
        .containsExactly("graph fine");
    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).reason())
        .contains("control flow not lowered for broken");
  }

  private static RuleRunResult run(
      final CountingLowering lowering, final List<RuleDefinition> rules) {
    final LanguageFrontend frontend =
        (unit) -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    final FrontendSupport java =
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            frontend,
            Set.of(RuleCapability.SYNTAX, RuleCapability.CFG, RuleCapability.DATAFLOW),
            lowering);
    return new RuleEngine(new GraphMatcher())
        .run(List.of(RuleEngineFixtures.unit("src/X.java")), List.of(java), rules);
  }

  private static RuleDefinition rule(final String id, final RuleCapability capability) {
    return RuleEngineFixtures.rule(id, capability);
  }

  private static ControlFlowGraph graph(final String body) {
    final ControlFlowGraph graph =
        GraphFixtures.graph(GraphFixtures.empty(), GraphFixtures.empty());
    return new ControlFlowGraph(body, AT, graph.blocks(), graph.entry(), graph.exit());
  }

  /** Counts the lowerings of a unit, and returns the same graphs and failures each time. */
  private static final class CountingLowering implements ControlFlowLowering {

    private final List<ControlFlowGraph> graphs;
    private final List<AnalysisFinding> failures;
    private int calls;

    CountingLowering(final List<ControlFlowGraph> graphs, final List<AnalysisFinding> failures) {
      this.graphs = graphs;
      this.failures = failures;
    }

    @Override
    public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
      calls++;
      return new ControlFlowResult(graphs, failures);
    }

    int calls() {
      return calls;
    }
  }

  /** Reports one match per graph the rule is given, naming the graph's body. */
  private static final class GraphMatcher implements RuleMatcher {

    @Override
    public CompiledRule compile(final RuleDefinition definition) {
      return new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return List.of();
        }

        @Override
        public List<RuleMatch> matches(
            final SyntaxNode root, final DependencyView dependencies, final FlowView flow) {
          final List<RuleMatch> found = new ArrayList<>();
          for (final ControlFlowGraph graph : flow.graphs()) {
            found.add(new RuleMatch(graph.span(), "graph " + graph.body()));
          }
          return found;
        }
      };
    }
  }
}
