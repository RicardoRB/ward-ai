package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Positional helpers shared by the taint steps: statement containment and sanitized spans. */
final class TaintSpans {
  private TaintSpans() {}

  /** Reports whether {@code inner} lies within {@code outer}. */
  static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** Returns the statement or condition node of a block that holds a span, if any. */
  static Optional<SyntaxNode> nodeContaining(final BasicBlock block, final Span span) {
    return block.nodes().stream().filter(node -> contains(node.span(), span)).findFirst();
  }

  /** Returns the source entry that a call or member read matches, if any. */
  static Optional<TaintEntry> sourceOf(final FlowSite site, final TaintSpec spec) {
    return firstMatch(spec.sources(), site);
  }

  /** Returns the sink entry that a call matches, if any. */
  static Optional<TaintEntry> sinkOf(final FlowSite site, final TaintSpec spec) {
    return firstMatch(spec.sinks(), site);
  }

  /**
   * Reports whether a source entry matches a site of the graph. Every taint starts at such a site,
   * so a graph without one has no taint and needs no data-flow analysis.
   */
  static boolean hasSource(final ControlFlowGraph graph, final TaintSpec spec) {
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (sourceOf(site, spec).isPresent()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Reports whether a sink entry matches a site of the graph; every finding is at such a site. */
  static boolean hasSink(final ControlFlowGraph graph, final TaintSpec spec) {
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (sinkOf(site, spec).isPresent()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reports whether a marked parameter of the body can reach a sink. Marking adds one parameter
   * site, so a body without a sink site, under a specification with no sink on parameters, needs
   * none of its marked copies built and analyzed.
   */
  static boolean mayReachSink(final ControlFlowGraph graph, final TaintSpec seeded) {
    return hasSink(graph, seeded)
        || seeded.sinks().stream().anyMatch(entry -> entry.kind() == FlowSiteKind.PARAMETER);
  }

  /** Reports whether a source, sink or sanitizer entry matches the site. */
  static boolean matched(final FlowSite site, final TaintSpec spec) {
    return firstMatch(spec.sources(), site).isPresent()
        || firstMatch(spec.sinks(), site).isPresent()
        || firstMatch(spec.sanitizers(), site).isPresent();
  }

  /** The first entry that matches the site; a loop, as this runs for every site of every graph. */
  private static Optional<TaintEntry> firstMatch(
      final List<TaintEntry> entries,
      final FlowSite site
  ) {
    for (final TaintEntry entry : entries) {
      if (entry.matches(site)) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }
}
