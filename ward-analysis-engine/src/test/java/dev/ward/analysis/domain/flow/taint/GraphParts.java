package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The small parts of the hand-written graphs of {@link MemberGraphs}. */
final class GraphParts {
  private static final int LAST_LINE = 99;
  private static final int BODY_END = 100_000;

  private GraphParts() {}

  static SyntaxNode leaf(final String text, final Span span) {
    return new SyntaxNode("token", span, Map.of("text", text), List.of());
  }

  static FlowSite call(final String name, final Span span, final List<Span> arguments) {
    return new FlowSite(FlowSiteKind.CALL, name, "", Optional.empty(), List.of(), arguments, span);
  }

  static FlowSite mark(
      final String qualifier,
      final SyntaxNode statement,
      final String... annotations
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        MemberLookups.RETURNED,
        qualifier,
        Optional.empty(),
        List.of(annotations),
        List.of(),
        statement.span()
    );
  }

  static ControlFlowGraph graph(
      final String name,
      final List<SyntaxNode> nodes,
      final List<VariableAccess> accesses,
      final List<FlowSite> sites
  ) {
    final BasicBlock entry =
        new BasicBlock(0, nodes, List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)), accesses, sites);
    final BasicBlock exit = new BasicBlock(1, List.of(), List.of(), List.of(), List.of());
    return new ControlFlowGraph(
        name,
        new Span(1, 1, LAST_LINE, 1, 0, BODY_END),
        List.of(entry, exit),
        0,
        1
    );
  }
}
