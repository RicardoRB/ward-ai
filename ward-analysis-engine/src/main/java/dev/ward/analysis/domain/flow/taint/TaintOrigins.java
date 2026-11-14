package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tainted writes of one graph. A source taints the write that its statement makes, or the
 * parameter's entry write. A write is tainted when its statement reads a value that reaches it from
 * a tainted write, outside any sanitizer argument. Each tainted write keeps the path that reached
 * it, which grows by one step per propagating write.
 */
final class TaintOrigins {

  private static final String ASSIGNED = "assigned to ";

  private final ControlFlowGraph graph;
  private final ReachingDefinitions reaching;
  private final TaintSpec spec;
  private final Map<AccessSite, Taint> tainted = new HashMap<>();

  private TaintOrigins(
      final ControlFlowGraph graph, final ReachingDefinitions reaching, final TaintSpec spec) {
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
      final ControlFlowGraph graph, final ReachingDefinitions reaching, final TaintSpec spec) {
    final TaintOrigins origins = new TaintOrigins(graph, reaching, spec);
    new TaintSeeds(graph, spec, origins.tainted).seed();
    origins.propagate();
    return origins.tainted;
  }

  /** Repeats until no write gains taint; writes are visited in block order, so paths are stable. */
  private void propagate() {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final AccessSite definition : reaching.definitions()) {
        if (!tainted.containsKey(definition) && inherit(definition)) {
          changed = true;
        }
      }
      changed |= TaintStores.apply(graph, reaching, spec, tainted);
    }
  }

  /** Taints a write when a read in its statement sees a tainted write, unless it is sanitized. */
  private boolean inherit(final AccessSite definition) {
    final BasicBlock block = graph.blocks().get(definition.block());
    final Optional<SyntaxNode> node = TaintSpans.nodeContaining(block, definition.access().span());
    if (node.isEmpty()) {
      return false;
    }
    for (final AccessSite use : reaching.uses()) {
      if (inheritsFrom(use, definition, block, node.get().span())) {
        return taintFrom(use, definition);
      }
    }
    return false;
  }

  private boolean inheritsFrom(
      final AccessSite use,
      final AccessSite definition,
      final BasicBlock block,
      final Span statement) {
    return use.block() == definition.block()
        && use.access().kind() == AccessKind.READ
        && TaintSpans.contains(statement, use.access().span())
        && !TaintSpans.sanitized(block, use.access().span(), spec)
        && reaching.reaching(use).stream().anyMatch(tainted::containsKey);
  }

  private boolean taintFrom(final AccessSite use, final AccessSite definition) {
    final AccessSite source =
        reaching.reaching(use).stream().filter(tainted::containsKey).findFirst().orElseThrow();
    final Taint from = tainted.get(source);
    final List<FlowStep> path = new ArrayList<>(from.path());
    path.add(new FlowStep(definition.access().span(), ASSIGNED + definition.access().name()));
    tainted.put(definition, new Taint(from.source(), path));
    return true;
  }
}
