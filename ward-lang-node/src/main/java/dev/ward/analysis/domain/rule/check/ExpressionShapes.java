package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The shapes of the expression tree that the unused-expression check reads. */
final class ExpressionShapes {

  static final String EXPRESSION_STATEMENT = "expressionStatement";
  static final String STATEMENT = "statement";
  static final String SEQUENCE = "expressionSequence";
  static final String SINGLE = "singleExpression";
  private static final String LITERAL = "literal";
  private static final String STRING = "StringLiteral";
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String INCREMENT = "++";
  private static final String DECREMENT = "--";
  private static final Set<String> USED_PREFIXES =
      Set.of("void", "delete", "await", "yield", "new", INCREMENT, DECREMENT);
  private static final Set<String> UNARY = Set.of("!", "-", "+", "~", "typeof");
  private static final int PARENTHESIZED_SIZE = 3;
  private static final int SINGLE_EXPRESSION = 1;

  private ExpressionShapes() {}

  /** Whether a source element is a directive: one string expression at the start of a body. */
  static boolean isStringElement(final SyntaxNode element) {
    return expressionStatementOf(element)
        .map(statement -> singleInner(singles(statement)))
        .orElse(false);
  }

  private static boolean singleInner(final List<SyntaxNode> expressions) {
    return expressions.size() == SINGLE_EXPRESSION && isStringLiteral(innerOf(expressions.get(0)));
  }

  /** The directive test: a string expression, parenthesized only when the option allows it. */
  static boolean isDirective(final List<SyntaxNode> expressions, final boolean ignoreDirectives) {
    if (expressions.size() != SINGLE_EXPRESSION) {
      return false;
    }
    final SyntaxNode expression = expressions.get(0);
    final boolean parenthesized = isParenthesized(expression);
    final SyntaxNode inner = parenthesized ? innerOf(expression) : expression;
    return isStringLiteral(inner) && (!parenthesized || ignoreDirectives);
  }

  /** The statement of a source element, when the element is one expression statement. */
  static Optional<SyntaxNode> expressionStatementOf(final SyntaxNode element) {
    for (final SyntaxNode child : element.children()) {
      if (STATEMENT.equals(child.kind())) {
        return child.children().stream()
            .filter(c -> EXPRESSION_STATEMENT.equals(c.kind()))
            .findFirst();
      }
    }
    return Optional.empty();
  }

  static boolean isStringLiteral(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    return children.size() == SINGLE_EXPRESSION
        && LITERAL.equals(children.get(0).kind())
        && children.get(0).children().stream().anyMatch(child -> STRING.equals(child.kind()));
  }

  static boolean isParenthesized(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    return children.size() == PARENTHESIZED_SIZE
        && OPEN_PAREN.equals(LeafText.of(children.get(0)))
        && CLOSE_PAREN.equals(LeafText.of(children.get(2)));
  }

  /** The expression wrapped by parentheses, or the expression itself when it is not wrapped. */
  static SyntaxNode innerOf(final SyntaxNode expression) {
    if (!isParenthesized(expression)) {
      return expression;
    }
    final List<SyntaxNode> inner = singles(expression.children().get(1));
    return inner.size() == SINGLE_EXPRESSION ? innerOf(inner.get(0)) : expression;
  }

  /** The single expressions of an expression sequence, or of the sequence a node holds. */
  static List<SyntaxNode> singles(final SyntaxNode node) {
    final List<SyntaxNode> items =
        SEQUENCE.equals(node.kind()) ? node.children() : sequenceItems(node);
    final List<SyntaxNode> singles = new ArrayList<>();
    for (final SyntaxNode item : items) {
      if (SINGLE.equals(item.kind())) {
        singles.add(item);
      }
    }
    return singles;
  }

  private static List<SyntaxNode> sequenceItems(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (SEQUENCE.equals(child.kind())) {
        return child.children();
      }
    }
    return List.of();
  }

  /** A prefix operator with a leading token: a used one is false, a unary one is true. */
  static Optional<Boolean> prefixed(final List<SyntaxNode> children) {
    if (!children.get(0).children().isEmpty()) {
      return Optional.empty();
    }
    final String first = LeafText.of(children.get(0));
    if (USED_PREFIXES.contains(first)) {
      return Optional.of(false);
    }
    return UNARY.contains(first) ? Optional.of(true) : Optional.empty();
  }
}
