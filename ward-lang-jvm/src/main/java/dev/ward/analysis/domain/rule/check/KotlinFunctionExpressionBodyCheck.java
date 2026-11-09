package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a function whose block body only returns a value or throws, so that an expression body
 * would do, as ktlint's {@code function-expression-body} rule does. A bare {@code return} is not
 * reported.
 */
public final class KotlinFunctionExpressionBodyCheck implements RuleCheck {

  private static final String FUN = "functionDeclaration";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String BLOCK = "block";
  private static final String STATEMENTS = "statements";
  private static final String JUMP = "jumpExpression";
  private static final String VALUE = "expression";
  private static final String RETURN = "return";
  private static final String THROW = "throw";
  private static final int SINGLE_STATEMENT = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the block
   */
  public KotlinFunctionExpressionBodyCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUN))) {
      final Optional<SyntaxNode> block = childOf(childOf(visit.node(), FUNCTION_BODY), BLOCK);
      if (block.isPresent() && isSingleJump(block.get())) {
        matches.add(CheckSupport.at(block.get().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isSingleJump(final SyntaxNode block) {
    return childOf(block, STATEMENTS)
        .map(KotlinStatementLists::statements)
        .filter(statementList -> statementList.size() == SINGLE_STATEMENT)
        .flatMap(statementList -> KotlinStatementLists.expression(statementList.get(0), JUMP))
        .filter(KotlinFunctionExpressionBodyCheck::isReturnOrThrowWithValue)
        .isPresent();
  }

  private static boolean isReturnOrThrowWithValue(final SyntaxNode jump) {
    final String keyword = jump.children().isEmpty() ? "" : SyntaxWalk.text(jump.children().get(0));
    final boolean hasValue = jump.children().stream().anyMatch(child -> VALUE.equals(child.kind()));
    return THROW.equals(keyword) || RETURN.equals(keyword) && hasValue;
  }

  private static Optional<SyntaxNode> childOf(
      final Optional<SyntaxNode> parent, final String kind) {
    return parent.flatMap(node -> childOf(node, kind));
  }

  private static Optional<SyntaxNode> childOf(final SyntaxNode parent, final String kind) {
    return parent.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }
}
