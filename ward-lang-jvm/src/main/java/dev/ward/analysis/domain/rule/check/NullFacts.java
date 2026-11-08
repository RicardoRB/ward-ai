package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.Span;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The joins of the backward half of the null data-flow. */
final class NullFacts {

  private NullFacts() {}

  /** Returns what holds at the end of a block with no successor: nothing, or everything. */
  static NullBackward.Fact ending(final BasicBlock block) {
    final boolean throwing =
        !block.nodes().isEmpty()
            && SyntaxWalk.text(block.nodes().get(block.nodes().size() - 1)).startsWith("throw");
    return throwing ? NullBackward.Fact.TOP : NullBackward.Fact.NONE;
  }

  /** Keeps the variables that both facts dereference, with the places of both. */
  static NullBackward.Fact meet(final NullBackward.Fact left, final NullBackward.Fact right) {
    if (left.everything()) {
      return right;
    }
    if (right.everything()) {
      return left;
    }
    final Map<Span, Set<NullBackward.Site>> met = new HashMap<>();
    left.sites()
        .forEach(
            (variable, sites) -> {
              if (right.has(variable)) {
                final Set<NullBackward.Site> union = new HashSet<>(sites);
                union.addAll(right.sites().get(variable));
                met.put(variable, union);
              }
            });
    return new NullBackward.Fact(false, met);
  }
}
