package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.ExpressionShapes.EXPRESSION_STATEMENT;
import static dev.ward.analysis.domain.rule.check.ExpressionShapes.SINGLE;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an expression statement that has no effect and calls nothing, like ESLint {@code
 * no-unused-expressions}. Assignments, calls, {@code new}, updates, {@code await}, {@code yield},
 * {@code delete} and {@code void} are used. The options allow a short-circuit, a ternary or a
 * tagged template to be used, count JSX as an expression when asked, and skip directives that are
 * wrapped in parentheses.
 */
public final class NodeUnusedExpressionsCheck implements RuleCheck {

  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final String SOURCE_ELEMENT = "sourceElement";
  private static final Set<String> WRAPPERS = Set.of(SOURCE_ELEMENT, ExpressionShapes.STATEMENT);
  private static final String ARGUMENTS = "arguments";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String JSX_ELEMENT = "jsxElement";
  private static final String JSX_FRAGMENT = "jsxFragment";
  private static final String THIS = "this";
  private static final String QUESTION = "?";
  private static final String OPTIONAL_CALL = "?.";
  private static final String NON_NULL = "!";
  private static final String INCREMENT = "++";
  private static final String DECREMENT = "--";
  private static final Set<String> MEMBERS = Set.of(".", "?.", "[");
  private static final Set<String> CASTS = Set.of("as", "satisfies");
  private static final Set<String> LOGICAL = Set.of("&&", "||", "??");
  private static final Set<String> ASSIGNMENTS =
      Set.of(
          "=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", ">>>=", "&=", "|=", "^=", "&&=",
          "||=", "??=");
  private static final Set<String> ALWAYS_DISALLOWED =
      Set.of(
          "identifier",
          "identifierName",
          "literal",
          "arrayLiteral",
          "objectLiteral",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "classExpression");
  private static final int UNARY_OPERAND = 1;
  private static final int SINGLE_EXPRESSION = 1;
  private static final int CONDITIONAL_SIZE = 5;
  private static final int CONDITIONAL_CONSEQUENT = 2;
  private static final int CONDITIONAL_ALTERNATE = 4;
  private static final int CALL_SIZE = 2;

  private final Options options;
  private final String message;

  /** The options of the check, as ESLint names them. */
  public record Options(
      boolean allowShortCircuit,
      boolean allowTernary,
      boolean allowTaggedTemplates,
      boolean enforceForJsx,
      boolean ignoreDirectives) {}

  /**
   * Creates the check.
   *
   * @param options the ESLint options
   * @param message the message of each finding
   */
  public NodeUnusedExpressionsCheck(final Options options, final String message) {
    this.options = Objects.requireNonNull(options, "options");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, false, matches);
    return List.copyOf(matches);
  }

  /** Walks the tree; the leading string statements of a source element list are a prologue. */
  private void collect(
      final SyntaxNode node, final boolean prologue, final List<RuleMatch> matches) {
    if (SOURCE_ELEMENTS.equals(node.kind())) {
      boolean elementsPrologue = true;
      for (final SyntaxNode element : node.children()) {
        elementsPrologue = elementsPrologue && ExpressionShapes.isStringElement(element);
        collect(element, elementsPrologue, matches);
      }
      return;
    }
    if (EXPRESSION_STATEMENT.equals(node.kind())) {
      checkStatement(node, prologue, matches);
    }
    final boolean carried = prologue && WRAPPERS.contains(node.kind());
    node.children().forEach(child -> collect(child, carried, matches));
  }

  private void checkStatement(
      final SyntaxNode statement, final boolean prologue, final List<RuleMatch> matches) {
    final List<SyntaxNode> expressions = ExpressionShapes.singles(statement);
    if (prologue && ExpressionShapes.isDirective(expressions, options.ignoreDirectives())) {
      return;
    }
    if (expressions.size() > SINGLE_EXPRESSION
        || expressions.size() == SINGLE_EXPRESSION && disallowed(expressions.get(0))) {
      matches.add(new RuleMatch(statement.span(), message));
    }
  }

  /** Whether ESLint reports an expression with no effect. */
  boolean disallowed(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    if (children.isEmpty()) {
      return THIS.equals(LeafText.of(expression));
    }
    if (children.size() == UNARY_OPERAND) {
      return single(children.get(0));
    }
    if (ExpressionShapes.isParenthesized(expression)) {
      final List<SyntaxNode> inner = ExpressionShapes.singles(children.get(1));
      return inner.size() > SINGLE_EXPRESSION
          || inner.size() == SINGLE_EXPRESSION && disallowed(inner.get(0));
    }
    return ExpressionShapes.prefixed(children)
        .orElseGet(() -> children.size() == CALL_SIZE ? postfix(children) : infix(children));
  }

  private boolean single(final SyntaxNode child) {
    if (child.children().isEmpty()) {
      return THIS.equals(LeafText.of(child));
    }
    if (JSX_ELEMENT.equals(child.kind()) || JSX_FRAGMENT.equals(child.kind())) {
      return options.enforceForJsx();
    }
    if (SINGLE.equals(child.kind())) {
      return disallowed(child);
    }
    return ALWAYS_DISALLOWED.contains(child.kind()) || TEMPLATE.equals(child.kind());
  }

  /** A postfix update, a call or a member, a non-null assertion, or a tagged template. */
  private boolean postfix(final List<SyntaxNode> children) {
    final SyntaxNode operator = children.get(1);
    if (ARGUMENTS.equals(operator.kind())) {
      return false;
    }
    if (TEMPLATE.equals(operator.kind())) {
      return !options.allowTaggedTemplates();
    }
    final String text = LeafText.of(operator);
    if (INCREMENT.equals(text) || DECREMENT.equals(text)) {
      return false;
    }
    return !NON_NULL.equals(text) || disallowed(children.get(0));
  }

  private boolean infix(final List<SyntaxNode> children) {
    return calledOrTagged(children)
        .orElseGet(() -> operatorUse(LeafText.of(children.get(1)), children));
  }

  /** A call and a tagged template are used, or a tagged template is when the option says so. */
  private Optional<Boolean> calledOrTagged(final List<SyntaxNode> children) {
    final SyntaxNode operator = children.get(1);
    if (ARGUMENTS.equals(operator.kind())) {
      return Optional.of(false);
    }
    if (TEMPLATE.equals(operator.kind())) {
      return Optional.of(!options.allowTaggedTemplates());
    }
    final boolean optionalCall =
        OPTIONAL_CALL.equals(LeafText.of(operator))
            && children.size() > CALL_SIZE
            && ARGUMENTS.equals(children.get(2).kind());
    return optionalCall ? Optional.of(false) : Optional.empty();
  }

  /** A member, an assignment, a logical or cast or conditional operator, or a binary operator. */
  private boolean operatorUse(final String text, final List<SyntaxNode> children) {
    if (MEMBERS.contains(text)) {
      return true;
    }
    if (ASSIGNMENTS.contains(text)) {
      return false;
    }
    if (LOGICAL.contains(text)) {
      return !options.allowShortCircuit() || disallowed(children.get(children.size() - 1));
    }
    if (CASTS.contains(text)) {
      return disallowed(children.get(0));
    }
    if (QUESTION.equals(text) && children.size() == CONDITIONAL_SIZE) {
      return !options.allowTernary()
          || disallowed(children.get(CONDITIONAL_CONSEQUENT))
          || disallowed(children.get(CONDITIONAL_ALTERNATE));
    }
    return true;
  }
}
