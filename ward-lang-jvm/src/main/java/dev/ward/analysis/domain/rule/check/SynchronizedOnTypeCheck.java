package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code synchronized} statement whose lock has a type from the given set, such as a
 * boxed primitive that may be shared by unrelated code. It reports nothing when the type is
 * unknown.
 */
public final class SynchronizedOnTypeCheck implements RuleCheck {

  private static final String STATEMENT = "statement";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Set<String> types;
  private final String message;

  /**
   * Creates the check.
   *
   * @param types qualified names of the types that must not be locked
   * @param message the message reported at the statement
   */
  public SynchronizedOnTypeCheck(final Set<String> types, final String message) {
    this.types = Set.copyOf(types);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      final SyntaxNode statement = visit.node();
      if (!statement.children().isEmpty()
          && SYNCHRONIZED.equals(statement.children().get(0).kind())
          && locksListedType(statement, facts)) {
        matches.add(CheckSupport.at(statement.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean locksListedType(final SyntaxNode statement, final UnitTypeFacts facts) {
    return statement.children().stream()
        .filter(child -> EXPRESSION.equals(child.kind()))
        .anyMatch(
            lock -> facts.typeOf(lock.span()).filter(t -> types.contains(t.name())).isPresent());
  }
}
