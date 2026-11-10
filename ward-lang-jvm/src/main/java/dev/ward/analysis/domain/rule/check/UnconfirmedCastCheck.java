package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a downcast {@code (Sub) value} of a variable or parameter, or of a call result, when the
 * method has no {@code instanceof} on the same operand. The cast is a downcast when the type of the
 * value is one of the supertypes of the cast type; it reports nothing when either type is unknown.
 */
public final class UnconfirmedCastCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD = "methodDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String TYPE_TYPE = "typeType";
  private static final String INSTANCEOF = "'instanceof'";
  private static final Pattern CALL = Pattern.compile("^[\\w.()\\[\\]<>,\"]*\\)$");
  private static final Pattern NAME = Pattern.compile("^\\w+$");
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final boolean callResult;
  private final String message;

  /**
   * Creates the check.
   *
   * @param callResult whether it judges casts of call results (true) or of variables (false)
   * @param message the message reported at the cast
   */
  public UnconfirmedCastCheck(final boolean callResult, final String message) {
    this.callResult = callResult;
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .filter(visit -> isCast(visit.node()) && operandMatches(visit.node()))
        .filter(visit -> downcast(visit.node(), types) && !confirmed(visit))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean isCast(final SyntaxNode node) {
    return node.children().size() == CAST_PARTS
        && TYPE_TYPE.equals(node.children().get(CAST_TYPE).kind());
  }

  private boolean operandMatches(final SyntaxNode cast) {
    final String operand = SyntaxWalk.text(cast.children().get(CAST_OPERAND));
    return callResult ? CALL.matcher(operand).matches() : NAME.matcher(operand).matches();
  }

  private static boolean downcast(final SyntaxNode cast, final UnitTypeFacts types) {
    final Optional<TypeRef> from = types.typeOf(cast.children().get(CAST_OPERAND).span());
    final Optional<TypeMembers> above = types.membersOf(cast.span());
    return from.isPresent()
        && above.isPresent()
        && from.get().arrayDepth() == 0
        && above.get().ancestors().stream().anyMatch(a -> a.name().equals(from.get().name()));
  }

  /** Returns whether the method that holds the cast tests the operand with instanceof. */
  private static boolean confirmed(final SyntaxWalk.Visit visit) {
    final String operand = SyntaxWalk.text(visit.node().children().get(CAST_OPERAND));
    return visit.ancestors().stream()
        .filter(node -> METHOD.equals(node.kind()) || CONSTRUCTOR.equals(node.kind()))
        .reduce((outer, inner) -> inner)
        .stream()
        .flatMap(method -> SyntaxWalk.descendants(method).stream())
        .anyMatch(
            node ->
                node.children().size() > 1
                    && INSTANCEOF.equals(node.children().get(1).kind())
                    && operand.equals(SyntaxWalk.text(node.children().get(0))));
  }
}
