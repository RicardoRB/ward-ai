package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.Span;
import java.util.Comparator;
import java.util.List;

/**
 * An argument that a sink guards, and the spans inside it that hold no taint.
 *
 * @param block the block of the sink
 * @param span the argument
 * @param clean the spans whose sources and reads do not count
 */
record GuardedArgument(BasicBlock block, Span span, List<Span> clean) {
  GuardedArgument {
    clean = List.copyOf(clean);
  }

  /** An argument with no clean part. */
  static GuardedArgument plain(final BasicBlock block, final Span span) {
    return new GuardedArgument(block, span, List.of());
  }

  /**
   * An argument of a sink. When the sink lists the call that is the whole argument, such as {@code
   * JSON.stringify(x)}, the arguments of that call are clean: nothing else starts where the
   * argument starts and runs longer, so a call chained on it or a string built around it is not
   * the whole argument.
   */
  static GuardedArgument of(final BasicBlock block, final Span span, final TaintEntry sink) {
    if (sink.exceptCalls().isEmpty()) {
      return plain(block, span);
    }
    final FlowSite longest =
        block.sites().stream()
            .filter(site -> TaintSpans.contains(span, site.span()))
            .filter(site -> site.span().startOffset() == span.startOffset())
            .max(Comparator.comparingInt(site -> site.span().endOffset()))
            .orElse(null);
    return new GuardedArgument(
        block,
        span,
        longest != null && sink.exempts(longest) ? longest.arguments() : List.of()
    );
  }

  /** Reports whether a span lies inside a clean part. */
  boolean isClean(final Span inner) {
    return clean.stream().anyMatch(part -> TaintSpans.contains(part, inner));
  }
}
