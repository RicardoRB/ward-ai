package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;

/**
 * Narrows what the accesses and the sites of one body report for the next node. A statement that
 * holds a lambda or another branching value is lowered in parts, and each part shows only its own
 * names and calls: the lambda's statements are nodes of their own, and the receiver of a lambda's
 * call is lowered before the lambda.
 */
final class KotlinView {

  private List<Span> hidden = List.of();
  private Span only;
  private boolean reads = true;
  private boolean writes = true;

  /**
   * Narrows the view until {@link #reset()}.
   *
   * @param hiddenSpans spans whose names and calls belong to other nodes
   * @param within the only span that is shown, or null for everything outside the hidden spans
   * @param showReads whether reads are shown
   * @param showWrites whether writes are shown
   */
  void narrow(
      final List<Span> hiddenSpans,
      final Span within,
      final boolean showReads,
      final boolean showWrites) {
    this.hidden = List.copyOf(hiddenSpans);
    this.only = within;
    this.reads = showReads;
    this.writes = showWrites;
  }

  /** Shows nothing until {@link #reset()}: the next node holds only synthetic accesses. */
  void showNothing() {
    narrow(List.of(), new Span(1, 1, 1, 1, 0, 0), false, false);
  }

  /** Shows everything again. */
  void reset() {
    narrow(List.of(), null, true, true);
  }

  /** Whether a span is shown. */
  boolean shows(final Span span) {
    return (only == null || contains(only, span))
        && hidden.stream().noneMatch(inner -> contains(inner, span));
  }

  /** Whether an access is shown. */
  boolean shows(final VariableAccess access) {
    final boolean write = access.kind() == dev.ward.analysis.domain.ir.AccessKind.WRITE;
    return (write ? writes : reads) && shows(access.span());
  }

  static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }
}
