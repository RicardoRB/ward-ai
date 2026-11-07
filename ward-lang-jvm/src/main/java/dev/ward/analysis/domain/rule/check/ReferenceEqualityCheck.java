package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code a == b} and {@code a != b} when both operands have a type from the given set, so
 * the comparison tests identity where the values should be compared with {@code equals}. The set
 * {@code *} stands for any reference type except {@code java.lang.Class} and enums, whose identity
 * is their equality; an enum is known from the operand's ancestors. It reports nothing when a type
 * is unknown.
 */
public final class ReferenceEqualityCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final Set<String> OPERATORS = Set.of("'=='", "'!='");
  private static final int OPERANDS = 3;
  private static final int RIGHT = 2;
  private static final String ANY = "*";
  private static final String CLASS = "java.lang.Class";
  private static final String ENUM = "java.lang.Enum";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final Set<String> types;
  private final String message;

  /**
   * Creates the check.
   *
   * @param types qualified names of the types whose values must not be compared by identity
   * @param message the message reported at the comparison
   */
  public ReferenceEqualityCheck(final Set<String> types, final String message) {
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final List<SyntaxNode> parts = visit.node().children();
      if (parts.size() == OPERANDS
          && OPERATORS.contains(parts.get(1).kind())
          && listed(parts.get(0), facts)
          && listed(parts.get(RIGHT), facts)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean listed(final SyntaxNode operand, final UnitTypeFacts facts) {
    final Optional<TypeRef> type = facts.typeOf(operand.span());
    if (type.isEmpty() || type.get().arrayDepth() != 0 || type.get().primitive()) {
      return false;
    }
    return types.contains(ANY)
        ? anyReference(operand, type.get(), facts)
        : types.contains(type.get().name());
  }

  private static boolean anyReference(
      final SyntaxNode operand, final TypeRef type, final UnitTypeFacts facts) {
    return !CLASS.equals(type.name())
        && facts
            .membersOf(operand.span())
            .map(members -> members.ancestors().stream().noneMatch(a -> ENUM.equals(a.name())))
            .orElse(false);
  }
}
