package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The tainted writes of one graph. A source taints the write that its statement makes, or the
 * parameter's entry write. A write is tainted when its statement reads a value that reaches it from
 * a tainted write, outside any sanitizer argument. Each tainted write keeps the path that reached
 * it, which grows by one step per propagating write. A read of the statement may sit in another
 * block of the graph, when the statement holds a conditional or logical operator that the graph
 * splits; it still counts, as the value of the write is made of it.
 */
final class TaintOrigins {
  private static final String ASSIGNED = "assigned to ";

  private final ControlFlowGraph graph;
  private final ReachingDefinitions reaching;
  private final TaintSpec spec;
  private final Map<AccessSite, Taint> tainted = new HashMap<>();
  private final Map<Integer, List<Span>> sanitizerArguments = new HashMap<>();

  private TaintOrigins(
      final ControlFlowGraph graph,
      final ReachingDefinitions reaching,
      final TaintSpec spec
  ) {
    this.graph = graph;
    this.reaching = reaching;
    this.spec = spec;
  }

  /**
   * Computes the tainted writes of a graph.
   *
   * @param graph the graph to analyse
   * @param reaching the reaching definitions of the graph
   * @param spec the taint specification
   * @return the taint of each tainted write
   */
  static Map<AccessSite, Taint> of(
      final ControlFlowGraph graph,
      final ReachingDefinitions reaching,
      final TaintSpec spec
  ) {
    final TaintOrigins origins = new TaintOrigins(graph, reaching, spec);
    new TaintSeeds(graph, spec, origins.tainted).seed();
    origins.propagate();
    return origins.tainted;
  }

  /**
   * Repeats until no write gains taint or turns proven; writes are visited in block order, so paths
   * are stable. A write that took an assumed taint is visited again, so a proven flow that reaches
   * it later replaces that taint.
   */
  private void propagate() {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final AccessSite definition : reaching.definitions()) {
        final Taint known = tainted.get(definition);
        if ((known == null || !known.proven()) && inherit(definition, known)) {
          changed = true;
        }
      }
      changed |= TaintStores.apply(graph, reaching, spec, tainted);
    }
  }

  /**
   * Taints a write when a read in its statement sees a tainted write, unless it is sanitized; of
   * several such reads, a proven flow wins.
   */
  private boolean inherit(final AccessSite definition, final Taint known) {
    final BasicBlock block = graph.blocks().get(definition.block());
    final Optional<SyntaxNode> node = TaintSpans.nodeContaining(block, definition.access().span());
    if (node.isEmpty()) {
      return false;
    }
    Taint best = null;
    for (final AccessSite use : reaching.uses()) {
      if (inheritsFrom(use, definition, node.get().span(), block)) {
        final Taint taint = taintFrom(use, definition, node.get().span());
        best = best == null ? taint : Taint.better(best, taint);
        if (best.proven()) {
          break;
        }
      }
    }
    if (best == null || known != null && !best.proven()) {
      return false;
    }
    tainted.put(definition, best);
    return true;
  }

  private boolean inheritsFrom(
      final AccessSite use,
      final AccessSite definition,
      final Span statement,
      final BasicBlock block
  ) {
    return use.access().kind() == AccessKind.READ
        && TaintSpans.contains(statement, use.access().span())
        && (use.block() == definition.block()
            || SplitValues.covers(block, use.access().span()))
        && reaching.reaching(use).stream().anyMatch(tainted::containsKey)
        && !sanitized(use);
  }

  /** Whether a use sits in a sanitizer argument; it does not change while taint propagates. */
  private boolean sanitized(final AccessSite use) {
    return sanitizerArguments
        .computeIfAbsent(use.block(), id -> SanitizedSpans.arguments(graph.blocks().get(id), spec))
        .stream()
        .anyMatch(argument -> TaintSpans.contains(argument, use.access().span()));
  }

  private Taint taintFrom(final AccessSite use, final AccessSite definition, final Span statement) {
    final Taint from =
        reaching.reaching(use).stream()
            .map(tainted::get)
            .filter(Objects::nonNull)
            .reduce(Taint::better)
            .orElseThrow();
    return from.extended(
        new FlowStep(definition.access().span(), ASSIGNED + definition.access().name()),
        UnfollowedCalls.through(graph.blocks().get(use.block()), statement, use.access().span())
    );
  }
}
