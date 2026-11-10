package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
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

  /** Reports whether a span lies within the argument of a sanitizer call of the block. */
  static boolean sanitized(final BasicBlock block, final Span span, final TaintSpec spec) {
    return block.sites().stream()
        .filter(site -> site.kind() == FlowSiteKind.CALL)
        .filter(site -> spec.sanitizers().stream().anyMatch(entry -> entry.matches(site)))
        .flatMap(site -> site.arguments().stream())
        .anyMatch(argument -> contains(argument, span));
  }

  /** Returns the source entry that a call or member read matches, if any. */
  static Optional<TaintEntry> sourceOf(final FlowSite site, final TaintSpec spec) {
    return spec.sources().stream().filter(entry -> entry.matches(site)).findFirst();
  }

  /** Returns the sink entry that a call matches, if any. */
  static Optional<TaintEntry> sinkOf(final FlowSite site, final TaintSpec spec) {
    return spec.sinks().stream().filter(entry -> entry.matches(site)).findFirst();
  }
}
