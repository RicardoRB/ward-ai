package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowDump;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared helpers for the control-flow tests: lowering, dumps and graph queries. */
final class JavaFlowFixtures {

  private JavaFlowFixtures() {}

  static ControlFlowResult lower(final String source) {
    final SourceUnit unit = new SourceUnit("src/C.java", AnalysisLanguage.JAVA, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new JavaControlFlow().lower(unit, parsed.tree());
  }

  static String dumps(final ControlFlowResult result) {
    final StringBuilder out = new StringBuilder();
    result.graphs().forEach(graph -> out.append(ControlFlowDump.render(graph)).append('\n'));
    return out.toString();
  }

  static List<String> names(final ControlFlowResult result) {
    return result.graphs().stream().map(ControlFlowGraph::body).toList();
  }

  static int blockWith(final ControlFlowGraph graph, final String source, final String snippet) {
    for (final BasicBlock block : graph.blocks()) {
      for (final SyntaxNode node : block.nodes()) {
        final String text = source.substring(node.span().startOffset(), node.span().endOffset());
        if (text.contains(snippet)) {
          return block.id();
        }
      }
    }
    throw new AssertionError("no block holds " + snippet);
  }

  static boolean hasEdge(
      final ControlFlowGraph graph, final int from, final int to, final FlowEdgeKind kind) {
    return graph.blocks().get(from).successors().contains(new FlowEdge(to, kind));
  }

  static List<Integer> successorsOf(final ControlFlowGraph graph, final int block) {
    return graph.blocks().get(block).successors().stream().map(FlowEdge::target).toList();
  }

  static List<Integer> incoming(final ControlFlowGraph graph, final int target) {
    return graph.blocks().stream()
        .filter(block -> block.successors().stream().anyMatch(edge -> edge.target() == target))
        .map(BasicBlock::id)
        .toList();
  }

  /** Reports whether control can move from block {@code from} to block {@code to}. */
  static boolean reachable(final ControlFlowGraph graph, final int from, final int to) {
    final Set<Integer> seen = new HashSet<>();
    final List<Integer> pending = new ArrayList<>(List.of(from));
    while (!pending.isEmpty()) {
      final int block = pending.remove(pending.size() - 1);
      if (block == to) {
        return true;
      }
      if (seen.add(block)) {
        graph.blocks().get(block).successors().forEach(edge -> pending.add(edge.target()));
      }
    }
    return false;
  }
}
