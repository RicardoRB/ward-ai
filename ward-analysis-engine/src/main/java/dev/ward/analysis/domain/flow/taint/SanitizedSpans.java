package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;

/** The spans that a sanitizer call of a block takes as arguments, and so leaves clean. */
final class SanitizedSpans {
  private SanitizedSpans() {}

  /**
   * Reports whether a span lies within the argument of a sanitizer call of the block, or within the
   * receiver that a sanitizing member read lists as its argument.
   */
  static boolean sanitized(final BasicBlock block, final Span span, final TaintSpec spec) {
    return arguments(block, spec).stream()
        .anyMatch(argument -> TaintSpans.contains(argument, span));
  }

  /**
   * The spans of the arguments of the sanitizer sites of a block; a caller that tests many spans of
   * one block reads them once.
   */
  static List<Span> arguments(final BasicBlock block, final TaintSpec spec) {
    return block.sites().stream()
        .filter(
            site ->
                site.kind() == FlowSiteKind.CALL
                    || site.kind() == FlowSiteKind.MEMBER_READ
                    || site.kind() == FlowSiteKind.QUERY_PART
                    || site.kind() == FlowSiteKind.VALIDATED
        )
        .filter(site -> spec.sanitizers().stream().anyMatch(entry -> entry.matches(site)))
        .flatMap(site -> site.arguments().stream())
        .toList();
  }
}
