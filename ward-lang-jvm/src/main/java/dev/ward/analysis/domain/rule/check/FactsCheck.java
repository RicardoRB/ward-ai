package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;

/**
 * A check that reads the type facts of its file and nothing else. A subclass names the facts it
 * needs and judges the tree with them; without facts it reports nothing, because every fact lookup
 * answers unknown.
 */
abstract class FactsCheck implements RuleCheck {

  private final FactNeed need;

  FactsCheck(final FactNeed need) {
    this.need = Objects.requireNonNull(need, "need");
  }

  @Override
  public final FactNeed needs() {
    return need;
  }

  @Override
  public final List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public final List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return check(root, types);
  }

  /** Returns the places in {@code root} where the check fails, in source order. */
  abstract List<RuleMatch> check(SyntaxNode root, UnitTypeFacts types);
}
