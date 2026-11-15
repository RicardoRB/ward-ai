package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The graph {@code c = ...; <store>; sink(c)} of the container store tests. */
final class TaintStoreGraphs {
  static final Span VALUE = new Span(2, 8, 2, 16, 18, 26);

  private static final Span DECLARATION = new Span(1, 1, 1, 11, 0, 10);
  private static final Span WRITE = new Span(1, 1, 1, 2, 0, 1);
  private static final Span STORE_READ = new Span(2, 1, 2, 2, 11, 12);
  private static final Span STORE_STATEMENT = new Span(2, 1, 2, 30, 11, 30);
  private static final Span SINK_READ = new Span(3, 6, 3, 7, 36, 37);
  private static final Span SINK_CALL = new Span(3, 1, 3, 9, 31, 39);
  private static final Span SINK_STATEMENT = new Span(3, 1, 3, 10, 31, 40);
  private static final Span MEMBER_WRITE = new Span(2, 1, 2, 17, 11, 27);
  private static final Span METHOD_CALL = new Span(2, 3, 2, 17, 13, 27);
  private static final Span WHOLE = new Span(1, 1, 3, 10, 0, 40);

  private TaintStoreGraphs() {}

  /** An assignment to a member of the qualifier, whose value is {@link #VALUE}. */
  static FlowSite memberWrite(final String qualifier) {
    return site(FlowSiteKind.MEMBER_WRITE, "f", qualifier, VALUE, MEMBER_WRITE);
  }

  /** A call of the name on the qualifier, whose argument 0 is {@link #VALUE}. */
  static FlowSite call(final String name, final String qualifier) {
    return site(FlowSiteKind.CALL, name, qualifier, VALUE, METHOD_CALL);
  }

  /**
   * The graph with the store, and with a call of {@code source} as the stored value when {@code
   * tainted} is set; the last statement passes {@code c} to {@code sink}.
   */
  static ControlFlowGraph graph(final FlowSite store, final boolean tainted) {
    final List<FlowSite> sites = new ArrayList<>();
    if (tainted) {
      sites.add(site(FlowSiteKind.CALL, "source", "", null, VALUE));
    }
    sites.add(store);
    sites.add(site(FlowSiteKind.CALL, "sink", "", SINK_READ, SINK_CALL));
    final BasicBlock body =
        new BasicBlock(
            0,
            List.of(statement(DECLARATION), statement(STORE_STATEMENT), statement(SINK_STATEMENT)),
            List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)),
            List.of(
                new VariableAccess("c", AccessKind.WRITE, WRITE, DECLARATION),
                new VariableAccess("c", AccessKind.READ, STORE_READ, DECLARATION),
                new VariableAccess("c", AccessKind.READ, SINK_READ, DECLARATION)),
            sites);
    return new ControlFlowGraph(
        "A#m(0)",
        WHOLE,
        List.of(body, new BasicBlock(1, List.of(), List.of(), List.of(), List.of())),
        0,
        1);
  }

  private static FlowSite site(
      final FlowSiteKind kind,
      final String name,
      final String qualifier,
      final Span argument,
      final Span span) {
    return new FlowSite(
        kind,
        name,
        qualifier,
        Optional.empty(),
        List.of(),
        argument == null ? List.of() : List.of(argument),
        span);
  }

  private static SyntaxNode statement(final Span span) {
    return new SyntaxNode("statement", span, Map.of(), List.of());
  }
}
