package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The taint of a returned value that a conditional or logical operator made in several blocks: an
 * operand that can be the value lies in a block of its own, and the block of the return marks its
 * span with a {@link SplitValues#SPLIT} site, so a read or a source in that operand belongs to the
 * return. The condition of a conditional is not marked, so it is not part of the value.
 */
final class SplitReturns {
  private SplitReturns() {}

  /**
   * The data flow of a graph that a return is judged against.
   *
   * @param reaching the reaching definitions of the graph
   * @param origins the tainted writes
   * @param spec the specification
   */
  record Flow(ReachingDefinitions reaching, Map<AccessSite, Taint> origins, TaintSpec spec) {}

  /** One return: the block it lies in and the value it gives back. */
  private record Return(BasicBlock block, Span value, Flow flow) {}

  /** The taint of the first operand of the value that a source reaches, if any. */
  static Optional<Taint> taint(
      final ControlFlowGraph graph,
      final BasicBlock block,
      final Span value,
      final Flow flow
  ) {
    final Return call = new Return(block, value, flow);
    for (final BasicBlock other : graph.blocks()) {
      if (other.id() == call.block().id()) {
        continue;
      }
      final Optional<Taint> found = source(call, other).or(() -> read(call, other));
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Optional<Taint> source(final Return call, final BasicBlock other) {
    return other.sites().stream()
        .filter(site -> inOperand(call, site.span()))
        .filter(site -> site.kind() != FlowSiteKind.PARAMETER)
        .filter(site -> !TaintSpans.sanitized(other, site.span(), call.flow().spec()))
        .flatMap(
            site -> TaintSpans.sourceOf(site, call.flow().spec()).map(
                entry -> origin(site, entry)
            ).stream()
        )
        .findFirst();
  }

  private static Taint origin(final FlowSite site, final TaintEntry entry) {
    return new Taint(
        site.name(),
        List.of(new FlowStep(site.span(), "source: " + site.name())),
        entry.assumptions()
    );
  }

  private static Optional<Taint> read(final Return call, final BasicBlock other) {
    return call.flow().reaching().uses().stream()
        .filter(use -> use.block() == other.id() && use.access().kind() == AccessKind.READ)
        .filter(use -> inOperand(call, use.access().span()))
        .filter(use -> !TaintSpans.sanitized(other, use.access().span(), call.flow().spec()))
        .flatMap(use -> call.flow().reaching().reaching(use).stream())
        .map(call.flow().origins()::get)
        .filter(taint -> taint != null)
        .findFirst();
  }

  private static boolean inOperand(final Return call, final Span span) {
    return TaintSpans.contains(call.value(), span) && SplitValues.covers(call.block(), span);
  }
}
