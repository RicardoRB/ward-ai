package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code Promise.reject} call whose reason could not be an Error, like ESLint {@code
 * prefer-promise-reject-errors}. A call with no reason is allowed when {@code allowEmptyReject} is
 * set.
 */
public final class NodePromiseRejectCheck implements RuleCheck {

  private static final String CALL_ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String SINGLE = "singleExpression";
  private static final String IDENTIFIER = "identifier";
  private static final String IDENTIFIER_NAME = "identifierName";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String PROMISE = "Promise";
  private static final String REJECT = "reject";
  private static final String UNDEFINED = "undefined";
  private static final String DOT = ".";
  private static final String OPTIONAL = "?.";
  private static final String QUESTION = "?";
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String AND = "&&";
  private static final Set<String> PREFIXES = Set.of("await", "yield", "new");
  private static final Set<String> MEMBERS = Set.of(".", "[", "?.");
  private static final Set<String> PLAIN_ASSIGN = Set.of("=", "&&=");
  private static final Set<String> NULLISH_ASSIGN = Set.of("||=", "??=");
  private static final Set<String> LOGICAL = Set.of("||", "??");
  private static final int CONDITIONAL_SIZE = 5;
  private static final int CONSEQUENT = 2;
  private static final int ALTERNATE = 4;
  private static final int CALLEE_PARTS = 3;
  private static final int CALL_SIZE = 2;
  private static final int PARENTHESIZED_PARTS = 3;
  private static final int ONE = 1;
  private static final int LEFT = 0;
  private static final int OPERATOR = 1;
  private static final int RIGHT = 2;

  private final boolean allowEmptyReject;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowEmptyReject whether a call with no reason is allowed
   * @param message the message of each finding
   */
  public NodePromiseRejectCheck(final boolean allowEmptyReject, final String message) {
    this.allowEmptyReject = allowEmptyReject;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SINGLE.equals(node.kind()) && isRejectCall(node) && unsafeReason(node)) {
      matches.add(new RuleMatch(node.span(), message));
    }
    node.children().forEach(child -> collect(child, matches));
  }

  private static boolean isRejectCall(final SyntaxNode call) {
    final List<SyntaxNode> children = call.children();
    return children.size() == CALL_SIZE
        && CALL_ARGUMENTS.equals(children.get(ONE).kind())
        && isPromiseReject(children.get(LEFT));
  }

  private static boolean isPromiseReject(final SyntaxNode callee) {
    final List<SyntaxNode> parts = callee.children();
    return parts.size() == CALLEE_PARTS
        && DOT.equals(LeafText.of(parts.get(OPERATOR)))
        && PROMISE.equals(LeafText.of(parts.get(LEFT)))
        && REJECT.equals(LeafText.of(parts.get(RIGHT)));
  }

  /** Whether the call has no reason when empty is not allowed, or a reason that is not an Error. */
  private boolean unsafeReason(final SyntaxNode call) {
    final Optional<SyntaxNode> reason = firstArgument(call.children().get(ONE));
    if (reason.isEmpty()) {
      return !allowEmptyReject;
    }
    return UNDEFINED.equals(LeafText.of(reason.get())) || !couldBeError(reason.get());
  }

  /** The first argument, whether the arguments hold them directly or in an argument list. */
  private static Optional<SyntaxNode> firstArgument(final SyntaxNode arguments) {
    final List<SyntaxNode> items = new ArrayList<>();
    for (final SyntaxNode child : arguments.children()) {
      items.addAll(ARGUMENT_LIST.equals(child.kind()) ? child.children() : List.of(child));
    }
    return items.stream()
        .filter(item -> ARGUMENT.equals(item.kind()))
        .findFirst()
        .flatMap(
            argument ->
                argument.children().stream().filter(c -> SINGLE.equals(c.kind())).findFirst());
  }

  /** The port of ESLint {@code couldBeError} to the expression tree. */
  static boolean couldBeError(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    if (children.isEmpty()) {
      return false;
    }
    if (children.size() == ONE) {
      final SyntaxNode child = children.get(LEFT);
      return SINGLE.equals(child.kind())
          ? couldBeError(child)
          : IDENTIFIER.equals(child.kind()) || IDENTIFIER_NAME.equals(child.kind());
    }
    if (children.size() == PARENTHESIZED_PARTS
        && OPEN_PAREN.equals(LeafText.of(children.get(LEFT)))
        && CLOSE_PAREN.equals(LeafText.of(children.get(RIGHT)))) {
      return lastSingle(children.get(OPERATOR));
    }
    return children.get(LEFT).children().isEmpty()
            && PREFIXES.contains(LeafText.of(children.get(LEFT)))
        || operator(children);
  }

  /** A parenthesized sequence is an error when its last expression is one. */
  private static boolean lastSingle(final SyntaxNode sequence) {
    final List<SyntaxNode> singles = new ArrayList<>();
    for (final SyntaxNode child : sequence.children()) {
      if (SINGLE.equals(child.kind())) {
        singles.add(child);
      }
    }
    return !singles.isEmpty() && couldBeError(singles.get(singles.size() - ONE));
  }

  private static boolean operator(final List<SyntaxNode> children) {
    if (children.size() == CALL_SIZE) {
      final SyntaxNode second = children.get(ONE);
      return CALL_ARGUMENTS.equals(second.kind())
          || TEMPLATE.equals(second.kind())
          || OPTIONAL.equals(LeafText.of(second));
    }
    final String op = LeafText.of(children.get(OPERATOR));
    if (children.size() == CONDITIONAL_SIZE && QUESTION.equals(op)) {
      return couldBeError(children.get(CONSEQUENT)) || couldBeError(children.get(ALTERNATE));
    }
    return MEMBERS.contains(op) || assignedOrLogical(op, children);
  }

  /** An assignment or a logical operator: the value it yields is the right side, or either side. */
  private static boolean assignedOrLogical(final String op, final List<SyntaxNode> children) {
    if (PLAIN_ASSIGN.contains(op) || AND.equals(op)) {
      return couldBeError(children.get(RIGHT));
    }
    return (NULLISH_ASSIGN.contains(op) || LOGICAL.contains(op))
        && (couldBeError(children.get(LEFT)) || couldBeError(children.get(RIGHT)));
  }
}
