package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an operation whose outcome does not depend on its operands, like ESLint {@code
 * no-constant-binary-expression}: a nullish coalescing on a left side that is never nullish, a
 * logical operator on a left side that is always truthy, and an equality against {@code null} or
 * {@code undefined} with a value that is never nullish. This is the expression-level core;
 * variables and calls other than {@code Boolean}, {@code String} and {@code Number} are not judged.
 */
public final class NodeConstantBinaryExpressionCheck implements RuleCheck {

  private static final String SINGLE = "singleExpression";
  private static final String LITERAL = "literal";
  private static final String NULL = "null";
  private static final String UNDEFINED = "undefined";
  private static final String VOID = "void";
  private static final String NEW = "new";
  private static final String OBJECT = "objectLiteral";
  private static final String ARRAY = "arrayLiteral";
  private static final String FUNCTION = "anonymousFunction";
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String CLASS = "classExpression";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String IDENTIFIER = "identifier";
  private static final String IDENTIFIER_NAME = "identifierName";
  private static final String BOOLEAN = "Boolean";
  private static final String STRING = "String";
  private static final String NUMBER = "Number";
  private static final String ASSIGN = "=";
  private static final String NULLISH = "??";
  private static final Set<String> LOGICAL = Set.of("&&", "||");
  private static final Set<String> EQUALITY = Set.of("==", "!=", "===", "!==");
  private static final Set<String> NON_NULLISH_CALLS = Set.of(BOOLEAN, STRING, NUMBER);
  private static final Set<String> BINARY_VALUES =
      Set.of(
          "+",
          "-",
          "*",
          "/",
          "%",
          "|",
          "^",
          "&",
          "**",
          "<<",
          ">>",
          ">>>",
          "<",
          ">",
          "<=",
          ">=",
          "in",
          "instanceof");
  private static final int BINARY_SIZE = 3;
  private static final int SINGLE_CHILD = 1;
  private static final int PREFIX_SIZE = 2;
  private static final int CALL_SIZE = 2;
  private static final int LEFT = 0;
  private static final int OPERATOR = 1;
  private static final int RIGHT = 2;

  private final String operandMessage;
  private final String shortCircuitMessage;

  /**
   * Creates the check.
   *
   * @param operandMessage the message for an equality with a constant side
   * @param shortCircuitMessage the message for a constant left side of a short-circuit operator
   */
  public NodeConstantBinaryExpressionCheck(
      final String operandMessage, final String shortCircuitMessage) {
    this.operandMessage = Objects.requireNonNull(operandMessage, "operandMessage");
    this.shortCircuitMessage = Objects.requireNonNull(shortCircuitMessage, "shortCircuitMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SINGLE.equals(node.kind()) && node.children().size() == BINARY_SIZE) {
      operation(node, matches);
    }
    node.children().forEach(child -> collect(child, matches));
  }

  private void operation(final SyntaxNode node, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = node.children();
    final String op = LeafText.of(children.get(OPERATOR));
    final SyntaxNode left = children.get(LEFT);
    final SyntaxNode right = children.get(RIGHT);
    if (LOGICAL.contains(op) && Values.truthinessConstant(left)) {
      matches.add(new RuleMatch(left.span(), shortCircuitMessage));
    } else if (NULLISH.equals(op) && Values.nullishConstant(left, false)) {
      matches.add(new RuleMatch(left.span(), shortCircuitMessage));
    } else if (EQUALITY.contains(op) && Values.equalityConstant(left, right)) {
      matches.add(new RuleMatch(node.span(), operandMessage));
    }
  }

  /** The constant-value queries of operands, which read only the shape of an expression. */
  private static final class Values {

    /**
     * An equality is constant when one side is {@code null} or {@code undefined} and the other is
     * never nullish.
     */
    private static boolean equalityConstant(final SyntaxNode left, final SyntaxNode right) {
      return nullish(left) && nullishConstant(right, false)
          || nullish(right) && nullishConstant(left, false);
    }

    /** Whether a value is {@code null}, {@code undefined} or a {@code void} expression. */
    private static boolean nullish(final SyntaxNode expression) {
      final SyntaxNode inner = unwrap(expression);
      final List<SyntaxNode> children = inner.children();
      final boolean voided =
          children.size() == PREFIX_SIZE && VOID.equals(LeafText.of(children.get(LEFT)));
      final String text = LeafText.of(inner);
      return NULL.equals(text) || UNDEFINED.equals(text) || voided;
    }

    /** Whether the value is never null or undefined; a value that is nullish is never reported. */
    static boolean nullishConstant(final SyntaxNode expression, final boolean nonNullish) {
      final SyntaxNode inner = unwrap(expression);
      if (nonNullish && nullish(inner)) {
        return false;
      }
      if (inner.children().size() == SINGLE_CHILD) {
        return neverNullish(inner.children().get(0));
      }
      return neverNullishOperation(inner);
    }

    private static boolean neverNullish(final SyntaxNode child) {
      if (LITERAL.equals(child.kind())) {
        return !NULL.equals(LeafText.of(child));
      }
      return OBJECT.equals(child.kind())
          || ARRAY.equals(child.kind())
          || FUNCTION.equals(child.kind())
          || ARROW.equals(child.kind())
          || CLASS.equals(child.kind())
          || TEMPLATE.equals(child.kind());
    }

    private static boolean neverNullishOperation(final SyntaxNode node) {
      final List<SyntaxNode> children = node.children();
      if (children.size() == PREFIX_SIZE) {
        return nullish(node);
      }
      final Optional<Boolean> binary =
          children.size() == BINARY_SIZE ? binaryOperation(children) : Optional.empty();
      if (binary.isPresent()) {
        return binary.get();
      }
      return NEW.equals(LeafText.of(children.get(LEFT))) || constructorCall(children);
    }

    /** The verdict of a binary operation that decides it, or empty when the operator does not. */
    private static Optional<Boolean> binaryOperation(final List<SyntaxNode> children) {
      final String operator = LeafText.of(children.get(OPERATOR));
      if (NULLISH.equals(operator)) {
        return Optional.of(nullishConstant(children.get(RIGHT), true));
      }
      if (ASSIGN.equals(operator)) {
        return Optional.of(nullishConstant(children.get(RIGHT), false));
      }
      return BINARY_VALUES.contains(operator) ? Optional.of(true) : Optional.empty();
    }

    private static boolean constructorCall(final List<SyntaxNode> children) {
      return children.size() == CALL_SIZE
          && NON_NULLISH_CALLS.contains(LeafText.of(children.get(LEFT)))
          && children.get(LEFT).children().size() == 1
          && (IDENTIFIER.equals(children.get(LEFT).children().get(0).kind())
              || IDENTIFIER_NAME.equals(children.get(LEFT).children().get(0).kind()));
    }

    /**
     * Whether a logical operator's left side is always truthy: a literal, a container or a
     * function.
     */
    private static boolean truthinessConstant(final SyntaxNode expression) {
      final SyntaxNode inner = unwrap(expression);
      final List<SyntaxNode> children = inner.children();
      if (children.size() != SINGLE_CHILD) {
        return false;
      }
      final SyntaxNode child = children.get(0);
      if (LITERAL.equals(child.kind())) {
        return true;
      }
      return OBJECT.equals(child.kind())
          || ARRAY.equals(child.kind())
          || FUNCTION.equals(child.kind())
          || ARROW.equals(child.kind())
          || CLASS.equals(child.kind());
    }

    /** The expression inside its parentheses, when it is parenthesized. */
    private static SyntaxNode unwrap(final SyntaxNode expression) {
      return ExpressionShapes.innerOf(expression);
    }
  }
}
