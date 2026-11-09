package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each use of {@code arguments} in a non-arrow function, like ESLint {@code
 * prefer-rest-params}. A non-computed member access such as {@code arguments.length} is allowed; a
 * computed access {@code arguments[0]} and any other use, such as a call argument, is reported.
 */
public final class PreferRestParamsCheck implements RuleCheck {

  private static final String ARGUMENTS = "arguments";
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "singleExpression";
  private static final String MEMBER_DOT = "'.'";
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String ANONYMOUS = "anonymousFunction";
  private static final Set<String> FUNCTIONS =
      Set.of(
          "functionDeclaration",
          "methodDefinition",
          "constructorDeclaration",
          "propertyMemberDeclaration",
          "getAccessor",
          "setAccessor",
          "propertyAssignment");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PreferRestParamsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final Deque<SyntaxNode> ancestors = new ArrayDeque<>();
    collect(root, ancestors, matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node, final Deque<SyntaxNode> ancestors, final List<RuleMatch> matches) {
    if (IDENTIFIER.equals(node.kind()) && ARGUMENTS.equals(text(node)) && reportable(ancestors)) {
      matches.add(new RuleMatch(node.span(), message));
    }
    ancestors.push(node);
    for (final SyntaxNode child : node.children()) {
      collect(child, ancestors, matches);
    }
    ancestors.pop();
  }

  /**
   * Tells whether the nearest non-arrow function owns this use, and the use is not a non-computed
   * member access on {@code arguments}.
   */
  private static boolean reportable(final Deque<SyntaxNode> ancestors) {
    final List<SyntaxNode> up = new ArrayList<>(ancestors);
    return ownerIsFunction(up) && !isNonComputedMember(up);
  }

  private static boolean ownerIsFunction(final List<SyntaxNode> up) {
    for (final SyntaxNode ancestor : up) {
      if (FUNCTIONS.contains(ancestor.kind())) {
        return true;
      }
      if (ANONYMOUS.equals(ancestor.kind()) && !isArrow(ancestor)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isArrow(final SyntaxNode anonymous) {
    return anonymous.children().stream().anyMatch(child -> ARROW.equals(child.kind()));
  }

  /**
   * The use is the object of a member access written with a dot. The identifier sits in an
   * expression (TypeScript wraps it in an identifierName first), which sits in the member
   * expression: {@code arguments} then {@code .length}.
   */
  private static boolean isNonComputedMember(final List<SyntaxNode> up) {
    int expression = -1;
    for (int index = 0; index < up.size() && expression < 0; index++) {
      if (EXPRESSION.equals(up.get(index).kind())) {
        expression = index;
      }
    }
    if (expression < 0 || expression + 1 >= up.size()) {
      return false;
    }
    final SyntaxNode object = up.get(expression);
    final SyntaxNode member = up.get(expression + 1);
    return EXPRESSION.equals(member.kind())
        && member.children().stream().anyMatch(child -> MEMBER_DOT.equals(child.kind()))
        && member.children().get(0).equals(object);
  }

  private static String text(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      final String token = node.fields().get("text");
      return token == null ? "" : token;
    }
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      out.append(text(child));
    }
    return out.toString();
  }
}
