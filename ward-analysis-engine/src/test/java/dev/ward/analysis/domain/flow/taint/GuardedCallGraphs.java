package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Optional;

/** The graphs of the guarded-call tests: a method with a check, and a caller of it. */
final class GuardedCallGraphs {
  static final Span DECLARATION = new Span(1, 1, 1, 2, 0, 1);
  static final Span ARGUMENT = new Span(5, 7, 5, 8, 60, 61);
  static final Span CALL = new Span(5, 1, 5, 9, 54, 62);
  static final int EXIT = 2;

  static final Span IF = new Span(2, 1, 4, 2, 10, 50);
  static final int GUARD_BLOCK = 1;

  private GuardedCallGraphs() {}

  static FlowEdge edge(final int target, final FlowEdgeKind kind) {
    return new FlowEdge(target, kind);
  }

  static BasicBlock block(
      final int id,
      final List<FlowEdge> edges,
      final List<VariableAccess> accesses,
      final List<FlowSite> sites
  ) {
    return new BasicBlock(id, List.of(), edges, accesses, sites);
  }

  static BasicBlock plain(final int id) {
    return block(id, List.of(), List.of(), List.of());
  }

  static ControlFlowGraph graph(
      final BasicBlock entry,
      final BasicBlock middle,
      final BasicBlock exit
  ) {
    return new ControlFlowGraph("A#check(1)", IF, List.of(entry, middle, exit), 0, EXIT);
  }

  static LocalMethod local(final ControlFlowGraph graph) {
    return new LocalMethod("check", "A", 1, graph, false);
  }

  /** The entry reaches the exit by {@code bypass}, and reaches the block with the mark. */
  static LocalMethod method(final FlowEdge bypass, final List<VariableAccess> extra) {
    final List<VariableAccess> parameter =
        new java.util.ArrayList<>(
            List.of(new VariableAccess("p", AccessKind.PARAMETER, DECLARATION, DECLARATION))
        );
    parameter.addAll(extra);
    final BasicBlock entry =
        block(0, List.of(edge(GUARD_BLOCK, FlowEdgeKind.FALSE), bypass), parameter, List.of());
    final FlowSite mark =
        new FlowSite(
            FlowSiteKind.CALL,
            GuardedCalls.GUARD,
            "p",
            Optional.empty(),
            List.of(),
            List.of(),
            IF
        );
    final BasicBlock guard =
        block(
            GUARD_BLOCK,
            List.of(edge(EXIT, FlowEdgeKind.NORMAL)),
            List.of(new VariableAccess("p", AccessKind.WRITE, IF, DECLARATION)),
            List.of(mark)
        );
    return local(graph(entry, guard, plain(EXIT)));
  }

  static ControlFlowGraph caller(final Span call, final Span argument) {
    final BasicBlock body =
        block(
            0,
            List.of(edge(1, FlowEdgeKind.NORMAL)),
            List.of(new VariableAccess("x", AccessKind.READ, ARGUMENT, DECLARATION)),
            List.of(
                new FlowSite(
                    FlowSiteKind.CALL,
                    "check",
                    "",
                    Optional.empty(),
                    List.of(),
                    List.of(argument),
                    call
                )
            )
        );
    return new ControlFlowGraph("B#run(0)", CALL, List.of(body, plain(1)), 0, 1);
  }
}
