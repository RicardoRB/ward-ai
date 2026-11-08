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
 * Reports a conditional expression whose two results are boxed numbers of different types, as in
 * {@code flag ? integer : longValue}: they are unboxed and promoted to one type, which can throw a
 * {@link NullPointerException} and changes the type of the result. It reports nothing when a type
 * is unknown.
 */
public final class ConditionalPromotionCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String QUESTION = "?";
  private static final String COLON = ":";
  private static final int PARTS = 5;
  private static final int THEN = 2;
  private static final int ELSE = 4;
  private static final Set<String> BOXES =
      Set.of(
          "java.lang.Byte",
          "java.lang.Short",
          "java.lang.Character",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Float",
          "java.lang.Double");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the conditional expression
   */
  public ConditionalPromotionCheck(final String message) {
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
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      if (promotes(visit.node(), types)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean promotes(final SyntaxNode node, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() != PARTS
        || !QUESTION.equals(SyntaxWalk.text(parts.get(1)))
        || !COLON.equals(SyntaxWalk.text(parts.get(THEN + 1)))) {
      return false;
    }
    final Optional<String> then = box(parts.get(THEN), types);
    final Optional<String> otherwise = box(parts.get(ELSE), types);
    return then.isPresent() && otherwise.isPresent() && !then.equals(otherwise);
  }

  private static Optional<String> box(final SyntaxNode result, final UnitTypeFacts types) {
    return types
        .typeOf(result.span())
        .filter(type -> type.arrayDepth() == 0 && !type.primitive())
        .map(TypeRef::name)
        .filter(BOXES::contains);
  }
}
