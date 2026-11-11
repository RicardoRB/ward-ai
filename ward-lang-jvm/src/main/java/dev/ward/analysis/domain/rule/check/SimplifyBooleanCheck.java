package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a boolean expression that a boolean literal makes redundant, as Checkstyle's {@code
 * SimplifyBooleanExpression} and PMD's {@code SimplifyBooleanExpressions} do: {@code x == true},
 * {@code x != false}, {@code x && true}, {@code false || x}, {@code !true} and {@code c ? true :
 * false}.
 */
public final class SimplifyBooleanCheck implements UnitCheck {
  private static final String EXPRESSION = "expression";
  private static final Set<String> OPERATORS = Set.of("'=='", "'!='", "'&&'", "'||'");
  private static final String NOT = "'!'";
  private static final String QUESTION = "'?'";
  private static final Set<String> LITERALS = Set.of("true", "false");
  private static final int BINARY_PARTS = 3;
  private static final int TERNARY_PARTS = 5;
  private static final int LEFT = 0;
  private static final int RIGHT = 2;
  private static final int OPERATOR = 1;
  private static final int WHEN_TRUE = 2;
  private static final int WHEN_FALSE = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the expression
   */
  public SimplifyBooleanCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(SimplifyBooleanCheck::redundant)
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static boolean redundant(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    return binary(parts) || negation(parts) || ternary(parts);
  }

  private static boolean binary(final List<SyntaxNode> parts) {
    return parts.size() == BINARY_PARTS
        && OPERATORS.contains(parts.get(OPERATOR).kind())
        && (literal(parts.get(LEFT)) || literal(parts.get(RIGHT)));
  }

  private static boolean negation(final List<SyntaxNode> parts) {
    return parts.size() == OPERATOR + 1
        && NOT.equals(parts.get(LEFT).kind())
        && literal(parts.get(OPERATOR));
  }

  private static boolean ternary(final List<SyntaxNode> parts) {
    return parts.size() == TERNARY_PARTS
        && QUESTION.equals(parts.get(OPERATOR).kind())
        && literal(parts.get(WHEN_TRUE))
        && literal(parts.get(WHEN_FALSE));
  }

  private static boolean literal(final SyntaxNode operand) {
    return EXPRESSION.equals(operand.kind()) && LITERALS.contains(SyntaxWalk.text(operand));
  }
}
