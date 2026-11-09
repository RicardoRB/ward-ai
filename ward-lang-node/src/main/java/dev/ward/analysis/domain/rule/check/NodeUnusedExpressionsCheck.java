package dev.ward.analysis.domain.rule.check;

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

  /** The options of the check, as ESLint names them. */
  public record Options(
      boolean allowShortCircuit,
      boolean allowTernary,
      boolean allowTaggedTemplates,
      boolean enforceForJsx,
      boolean ignoreDirectives) {}

  private static final String STATEMENT = "statement";
  private static final String SOURCE_ELEMENT = "sourceElement";
  private static final Set<String> WRAPPERS = Set.of(SOURCE_ELEMENT, STATEMENT);
  private static final String EXPRESSION_STATEMENT = "expressionStatement";
  private static final String SEQUENCE = "expressionSequence";
  private static final String SINGLE = "singleExpression";
  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final String LITERAL = "literal";
  private static final String STRING = "StringLiteral";
  private static final String ARGUMENTS = "arguments";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String JSX_ELEMENT = "jsxElement";
  private static final String JSX_FRAGMENT = "jsxFragment";
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String THIS = "this";
  private static final String QUESTION = "?";
  private static final String OPTIONAL_CALL = "?.";
  private static final String NON_NULL = "!";
  private static final String INCREMENT = "++";
  private static final String DECREMENT = "--";
  private static final Set<String> MEMBERS = Set.of(".", "?.", "[");
  private static final Set<String> CASTS = Set.of("as", "satisfies");
  private static final Set<String> LOGICAL = Set.of("&&", "||", "??");
  private static final Set<String> USED_PREFIXES =
      Set.of("void", "delete", "await", "yield", "new", INCREMENT, DECREMENT);
  private static final Set<String> UNARY = Set.of("!", "-", "+", "~", "typeof");
  private static final Set<String> ASSIGNMENTS =
      Set.of(
          "=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", ">>>=", "&=", "|=", "^=", "&&=",
          "||=", "??=");
  private static final Set<String> ALWAYS_DISALLOWED =
      Set.of(
          "identifier",
          "identifierName",
          LITERAL,
          "arrayLiteral",
          "objectLiteral",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "classExpression");
  private static final String MESSAGE =
      "Expected an assignment or function call and instead saw an expression.";
  private static final int CONDITIONAL_SIZE = 5;
  private static final int CONDITIONAL_CONSEQUENT = 2;
  private static final int CONDITIONAL_ALTERNATE = 4;
  private static final int PARENTHESIZED_SIZE = 3;
  private static final int UNARY_OPERAND = 1;

  private final Options options;
  private final String message;

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
      collectElements(node, matches);
      return;
    }
    if (EXPRESSION_STATEMENT.equals(node.kind())) {
      checkStatement(node, prologue, matches);
    }
    final boolean carried = prologue && WRAPPERS.contains(node.kind());
    node.children().forEach(child -> collect(child, carried, matches));
  }

  private void collectElements(final SyntaxNode elements, final List<RuleMatch> matches) {
    boolean prologue = true;
    for (final SyntaxNode element : elements.children()) {
      prologue = prologue && isStringElement(element);
      collect(element, prologue, matches);
    }
  }

  private void checkStatement(
      final SyntaxNode statement, final boolean prologue, final List<RuleMatch> matches) {
    final List<SyntaxNode> expressions = singles(statement);
    if (prologue && isDirective(expressions)) {
      return;
    }
    if (expressions.size() > 1 || expressions.size() == 1 && disallowed(expressions.get(0))) {
      matches.add(new RuleMatch(statement.span(), message));
    }
  }

  /**
   * A string statement is a directive; ESLint skips it, and a parenthesized one only when asked.
   */
  private boolean isDirective(final List<SyntaxNode> expressions) {
    if (expressions.size() != 1) {
      return false;
    }
    final SyntaxNode expression = expressions.get(0);
    final boolean parenthesized = isParenthesized(expression);
    final SyntaxNode inner = parenthesized ? innerOf(expression) : expression;
    return isStringLiteral(inner) && (!parenthesized || options.ignoreDirectives());
  }

  private boolean isStringElement(final SyntaxNode element) {
    final Optional<SyntaxNode> statement = expressionStatementOf(element);
    if (statement.isEmpty()) {
      return false;
    }
    final List<SyntaxNode> expressions = singles(statement.get());
    return expressions.size() == 1 && isStringLiteral(innerOf(expressions.get(0)));
  }

  private static Optional<SyntaxNode> expressionStatementOf(final SyntaxNode element) {
    for (final SyntaxNode child : element.children()) {
      if (STATEMENT.equals(child.kind())) {
        return child.children().stream()
            .filter(c -> EXPRESSION_STATEMENT.equals(c.kind()))
            .findFirst();
      }
    }
    return Optional.empty();
  }

  private static boolean isStringLiteral(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    return children.size() == 1
        && LITERAL.equals(children.get(0).kind())
        && children.get(0).children().stream().anyMatch(child -> STRING.equals(child.kind()));
  }

  private static boolean isParenthesized(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    return children.size() == PARENTHESIZED_SIZE
        && OPEN_PAREN.equals(LeafText.of(children.get(0)))
        && CLOSE_PAREN.equals(LeafText.of(children.get(2)));
  }

  /** The expression wrapped by parentheses, or the expression itself when it is one. */
  private static SyntaxNode innerOf(final SyntaxNode expression) {
    if (!isParenthesized(expression)) {
      return expression;
    }
    final List<SyntaxNode> inner = singles(expression.children().get(1));
    return inner.size() == 1 ? innerOf(inner.get(0)) : expression;
  }

  /** The single expressions of an expression sequence, or of the sequence a node holds. */
  private static List<SyntaxNode> singles(final SyntaxNode node) {
    final List<SyntaxNode> items =
        SEQUENCE.equals(node.kind()) ? node.children() : sequenceItems(node);
    return items.stream().filter(item -> SINGLE.equals(item.kind())).toList();
  }

  private static List<SyntaxNode> sequenceItems(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (SEQUENCE.equals(child.kind())) {
        return child.children();
      }
    }
    return List.of();
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
    if (isParenthesized(expression)) {
      final List<SyntaxNode> inner = singles(children.get(1));
      return inner.size() > 1 || inner.size() == 1 && disallowed(inner.get(0));
    }
    final String first = LeafText.of(children.get(0));
    if (children.get(0).children().isEmpty() && USED_PREFIXES.contains(first)) {
      return false;
    }
    if (children.get(0).children().isEmpty() && UNARY.contains(first)) {
      return true;
    }
    return children.size() == 2 ? postfix(children) : infix(children);
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
    return NON_NULL.equals(text) ? disallowed(children.get(0)) : true;
  }

  private boolean infix(final List<SyntaxNode> children) {
    final SyntaxNode operator = children.get(1);
    if (ARGUMENTS.equals(operator.kind())) {
      return false;
    }
    if (TEMPLATE.equals(operator.kind())) {
      return !options.allowTaggedTemplates();
    }
    final String text = LeafText.of(operator);
    if (OPTIONAL_CALL.equals(text)
        && children.size() > 2
        && ARGUMENTS.equals(children.get(2).kind())) {
      return false;
    }
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
