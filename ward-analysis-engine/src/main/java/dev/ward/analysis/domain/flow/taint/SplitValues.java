package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.Span;

/**
 * Statements whose value is made in several blocks. A frontend that splits a conditional or logical
 * operator over blocks marks the join block with a site of this name for each operand that can be
 * the value; a read or a source inside such an operand belongs to the statement of the join block.
 */
final class SplitValues {
  /** The name of the site that marks an operand of a statement whose value is made elsewhere. */
  static final String SPLIT = "<value-split>";

  private SplitValues() {}

  /** Reports whether the block holds a statement that takes the value at the span from others. */
  static boolean covers(final BasicBlock block, final Span span) {
    return block.sites().stream()
        .anyMatch(site -> SPLIT.equals(site.name()) && TaintSpans.contains(site.span(), span));
  }
}
