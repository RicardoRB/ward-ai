package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reports a switch over an enum that has no {@code default} and misses a constant of the enum. */
public final class NonExhaustiveSwitchCheck implements RuleCheck {

  private static final String STATEMENT = "statement";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the switch
   */
  public NonExhaustiveSwitchCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public Set<TypeNeed> typeNeeds() {
    return Set.of(TypedChecks.ENUM_SELECTORS);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final dev.ward.analysis.domain.rule.FlowView flow,
      final dev.ward.analysis.domain.rule.ScopeView scopes,
      final dev.ward.analysis.domain.ir.SourceUnit unit,
      final UnitTypeFacts types) {
    final Map<String, Set<String>> declared = EnumSwitch.declaredConstants(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      EnumSwitch.of(visit.node(), declared, types).ifPresent(sw -> report(sw, matches));
    }
    return List.copyOf(matches);
  }

  private void report(final EnumSwitch sw, final List<RuleMatch> matches) {
    if (sw.defaultLabel().isEmpty() && !sw.isExhaustive()) {
      matches.add(CheckSupport.at(sw.node().span(), message));
    }
  }
}
