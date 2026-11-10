package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/** A check that may read the path and text of the file, besides its syntax tree. */
@FunctionalInterface
interface UnitCheck extends RuleCheck {

  @Override
  default List<RuleMatch> matches(final SyntaxNode root) {
    return check(root, SourceFile.UNKNOWN);
  }

  @Override
  default List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return check(root, SourceFile.of(unit));
  }

  /** Returns the places in {@code root} where the check fails, in source order. */
  List<RuleMatch> check(SyntaxNode root, SourceFile file);
}
