package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Reports each function expression passed directly as a call argument that an arrow function could
 * replace, like ESLint's prefer-arrow-callback. Generators, functions that use {@code arguments},
 * {@code super} or {@code new.target}, and named functions that refer to their own name are left
 * alone; the options decide the named and the {@code this} cases.
 */
public final class NodeArrowCallbackCheck implements RuleCheck {

  private static final String ARGUMENT = "argument";
  private static final String ARGUMENTS = "arguments";
  private static final String ANONYMOUS = "anonymousFunction";
  private static final String EXPRESSION = "singleExpression";
  private static final String BIND = "bind";
  private static final String THIS = "(this)";
  private static final int CALLEE_PARTS = 2;
  private static final int MEMBER_PARTS = 3;

  private final boolean allowNamedFunctions;
  private final boolean allowUnboundThis;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowNamedFunctions whether a named function expression is left alone
   * @param allowUnboundThis whether a function that uses {@code this} is left alone
   * @param message the message of each finding
   */
  public NodeArrowCallbackCheck(
      final boolean allowNamedFunctions, final boolean allowUnboundThis, final String message) {
    this.allowNamedFunctions = allowNamedFunctions;
    this.allowUnboundThis = allowUnboundThis;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    visit(root, found);
    return List.copyOf(found);
  }

  private void visit(final SyntaxNode node, final List<RuleMatch> found) {
    if (ARGUMENT.equals(node.kind())) {
      NodeSyntax.code(node).stream()
          .filter(part -> EXPRESSION.equals(part.kind()))
          .findFirst()
          .ifPresent(argument -> callback(argument).ifPresent(found::add));
    }
    node.children().forEach(child -> visit(child, found));
  }

  /** The match for an argument that is a callback to report, if it is one. */
  private Optional<RuleMatch> callback(final SyntaxNode argument) {
    final Optional<SyntaxNode> direct = functionOf(argument);
    if (direct.isPresent()) {
      return report(direct.get(), false);
    }
    final Optional<SyntaxNode> bound = boundThis(argument);
    return bound.isPresent() ? report(bound.get(), true) : Optional.empty();
  }

  /** The function expression of a singleExpression that holds only the function. */
  private static Optional<SyntaxNode> functionOf(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeSyntax.code(expression);
    return parts.size() == 1 && ANONYMOUS.equals(parts.get(0).kind())
        ? Optional.of(parts.get(0))
        : Optional.empty();
  }

  /** The function of {@code function (...) {...}.bind(this)} when the argument is that call. */
  private static Optional<SyntaxNode> boundThis(final SyntaxNode argument) {
    final List<SyntaxNode> call = NodeSyntax.code(argument);
    if (call.size() != CALLEE_PARTS
        || !ARGUMENTS.equals(call.get(1).kind())
        || !THIS.equals(NodeSyntax.text(call.get(1)))) {
      return Optional.empty();
    }
    final List<SyntaxNode> member = NodeSyntax.code(call.get(0));
    if (member.size() != MEMBER_PARTS || !BIND.equals(NodeSyntax.text(member.get(2)))) {
      return Optional.empty();
    }
    return functionOf(member.get(0));
  }

  private Optional<RuleMatch> report(final SyntaxNode function, final boolean bound) {
    final Matcher head = ArrowCallbackSyntax.FUNCTION.matcher(NodeSyntax.text(function));
    if (!head.find() || head.group(1) != null || skippedName(function, head.group(2))) {
      return Optional.empty();
    }
    return skippedUses(function, bound)
        ? Optional.empty()
        : Optional.of(new RuleMatch(function.span(), message));
  }

  private boolean skippedName(final SyntaxNode function, final String name) {
    return name != null
        && (allowNamedFunctions || ArrowCallbackSyntax.referencesItself(function, name));
  }

  private boolean skippedUses(final SyntaxNode function, final boolean bound) {
    final ArrowCallbackSyntax.Uses uses = ArrowCallbackSyntax.ownUses(function);
    return uses.arguments()
        || uses.superOrMeta()
        || (!bound && allowUnboundThis && uses.thisUsed());
  }
}
