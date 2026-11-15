package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;

/**
 * Lowers Java and TypeScript or JavaScript sources and reads their reaching writes and findings.
 */
final class DataFlowFixtures {

  static final Set<RuleCapability> FLOW =
      Set.of(
          RuleCapability.SYNTAX, RuleCapability.CFG, RuleCapability.DATAFLOW, RuleCapability.TAINT);

  private DataFlowFixtures() {}

  static List<Integer> reachingLines(final ControlFlowGraph graph, final String name) {
    return reachingWrites(graph, name).stream().map(access -> access.span().startLine()).toList();
  }

  static List<VariableAccess> reachingWrites(final ControlFlowGraph graph, final String name) {
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    return reaching.uses().stream()
        .filter(use -> use.access().name().equals(name))
        .flatMap(use -> reaching.reaching(use).stream())
        .map(AccessSite::access)
        .toList();
  }

  /** The only write that reaches the read is the binding at the start of the construct. */
  static void assertBindingWriteReaches(
      final String source, final List<VariableAccess> writes, final String name, final String use) {
    assertThat(writes).hasSize(1);
    final VariableAccess write = writes.get(0);
    assertThat(write.kind()).isEqualTo(AccessKind.WRITE);
    assertThat(write.name()).isEqualTo(name);
    assertThat(write.span().startOffset()).isLessThan(source.indexOf(use));
    assertThat(source.substring(write.span().startOffset(), write.span().endOffset()))
        .isEqualTo(name);
  }

  static ControlFlowGraph javaScript(final String source, final String body) {
    return graph(new NodeControlFlow(), AnalysisLanguage.JAVASCRIPT, "a.js", source, body);
  }

  static ControlFlowGraph java(final String source, final String body) {
    return graph(new JavaControlFlow(), AnalysisLanguage.JAVA, "A.java", source, body);
  }

  static ControlFlowGraph typescript(final String source, final String body) {
    return graph(new NodeControlFlow(), AnalysisLanguage.TYPESCRIPT, "a.ts", source, body);
  }

  static ControlFlowGraph graph(
      final ControlFlowLowering lowering,
      final AnalysisLanguage language,
      final String path,
      final String source,
      final String body) {
    final SourceUnit unit = new SourceUnit(path, language, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return lowering.lower(unit, parsed.tree()).graphs().stream()
        .filter(graph -> graph.body().startsWith(body))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph " + body));
  }

  static List<AnalysisFinding> unusedFindings(
      final AnalysisLanguage language, final String source) {
    final String path = language == AnalysisLanguage.JAVA ? "A.java" : "a.ts";
    final ControlFlowLowering lowering =
        language == AnalysisLanguage.JAVA ? new JavaControlFlow() : new NodeControlFlow();
    final FrontendSupport support =
        new FrontendSupport(language, new AntlrLanguageFrontend(), FLOW, lowering);
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream()
                .filter(rule -> rule.id().endsWith("/unused-assignment"))
                .filter(rule -> rule.languages().contains(language))
                .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(List.of(new SourceUnit(path, language, source)), List.of(support), rules);
    assertThat(result.executionErrors()).isEmpty();
    return result.findings();
  }
}
