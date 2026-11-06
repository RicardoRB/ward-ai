package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fails each outermost boolean expression that has more logical or bitwise operators than the
 * limit. A chain is outermost when no enclosing expression is a chain of the same kind. Operators
 * inside a lambda or an anonymous class belong to that scope and are not counted.
 */
public final class BooleanOperatorCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final Set<String> OPERATORS = Set.of("'&&'", "'||'", "'&'", "'|'", "'^'");
  private static final Set<String> SCOPES = Set.of("lambdaExpression", "classBody");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed number of operators in one boolean expression
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public BooleanOperatorCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    visit(root, false, matches);
    return List.copyOf(matches);
  }

  private void visit(final SyntaxNode node, final boolean inChain, final List<RuleMatch> matches) {
    final boolean chain = isChain(node);
    if (chain && !inChain) {
      report(node, operators(node), matches);
    }
    final boolean below = chain || (inChain && !SCOPES.contains(node.kind()));
    for (final SyntaxNode child : node.children()) {
      visit(child, below, matches);
    }
  }

  private void report(final SyntaxNode node, final int count, final List<RuleMatch> matches) {
    if (count > max) {
      matches.add(
          new RuleMatch(
              node.span(),
              CheckSupport.fill(message, String.valueOf(count), String.valueOf(max))));
    }
  }

  /** Tells whether the node is an expression whose own operator is a logical or bitwise one. */
  private static boolean isChain(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind())
        && node.children().stream().anyMatch(child -> OPERATORS.contains(child.kind()));
  }

  private static int operators(final SyntaxNode node) {
    int count = 0;
    for (final SyntaxNode child : node.children()) {
      if (OPERATORS.contains(child.kind())) {
        count++;
      }
      if (!SCOPES.contains(child.kind())) {
        count += operators(child);
      }
    }
    return count;
  }
}
