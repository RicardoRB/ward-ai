package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonFlowSyntax;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a function that returns a value on some path and returns nothing on another, like Pylint
 * {@code R1710}. A function is consistent when every return has a value and the body always ends in
 * a return, or when no return has a value. A bare {@code return} and a fall-through end both return
 * nothing; {@code return None} has a value. The check reads the syntax tree, as Pylint does.
 */
public final class InconsistentReturnCheck implements RuleCheck {

  private static final String RETURN = "return_stmt";
  private static final String FUNCTION = "funcdef";
  private static final String BLOCK = "block";
  private static final String CLASS = "classdef";
  private static final String LAMBDA = "lambdef";
  private static final String LAMBDA_NO_CONDITION = "lambdef_nocond";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public InconsistentReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Checks every function of the file.
   *
   * @param root the syntax tree of the file
   * @return one finding per inconsistent function, at its definition
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    return matches(root);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (FUNCTION.equals(node.kind()) && inconsistent(node)) {
      found.add(new RuleMatch(node.span(), message));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  private static boolean inconsistent(final SyntaxNode function) {
    final SyntaxNode body = PythonFlowSyntax.child(function, BLOCK);
    final List<SyntaxNode> returns = new ArrayList<>();
    collectReturns(body, returns);
    final long explicit = returns.stream().filter(InconsistentReturnCheck::hasValue).count();
    if (explicit == 0) {
      return false;
    }
    return explicit != returns.size() || !ReturnEnds.endsInReturn(body);
  }

  /**
   * The returns of a body, leaving out the nested functions and classes, which return for
   * themselves.
   */
  private static void collectReturns(final SyntaxNode node, final List<SyntaxNode> returns) {
    if (isScope(node)) {
      return;
    }
    if (RETURN.equals(node.kind())) {
      returns.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collectReturns(child, returns);
    }
  }

  private static boolean isScope(final SyntaxNode node) {
    return FUNCTION.equals(node.kind())
        || CLASS.equals(node.kind())
        || LAMBDA.equals(node.kind())
        || LAMBDA_NO_CONDITION.equals(node.kind());
  }

  /** A return has a value when it has a child after the {@code return} keyword. */
  private static boolean hasValue(final SyntaxNode ret) {
    return ret.children().stream().anyMatch(child -> !child.children().isEmpty());
  }
}
