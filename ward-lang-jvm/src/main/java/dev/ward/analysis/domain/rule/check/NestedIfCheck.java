package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each if statement nested at the problem depth, as PMD does: the outermost if has depth
 * one, and the statement that reaches the problem depth is reported, once per chain. An {@code else
 * if} stays at the depth of its chain; the statement of a plain {@code else} is one level deeper.
 */
public final class NestedIfCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String IF_PREFIX = "if(";
  private static final int MIN_PROBLEM_DEPTH = 1;
  private static final int THEN = 4;

  private final int problemDepth;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problemDepth the nesting level of if statements that is reported, counting from one
   * @param message the message reported at the if statement
   * @throws IllegalArgumentException if the problem depth is below one
   */
  public NestedIfCheck(final int problemDepth, final String message) {
    if (problemDepth < MIN_PROBLEM_DEPTH) {
      throw new IllegalArgumentException("problem-depth must be at least 1");
    }
    this.problemDepth = problemDepth;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .filter(visit -> isIf(visit.node()))
        .filter(visit -> nesting(visit.ancestors(), visit.node()) == problemDepth - 1)
        .map(visit -> CheckSupport.at(visit.node().span(), filled()))
        .toList();
  }

  private String filled() {
    final String depth = String.valueOf(problemDepth);
    return CheckSupport.fill(message, depth, depth);
  }

  private static boolean isIf(final SyntaxNode node) {
    return STATEMENT.equals(node.kind()) && SyntaxWalk.text(node).startsWith(IF_PREFIX);
  }

  /**
   * The nesting of an if statement: each enclosing if adds one when the statement is in its then
   * branch or in an else branch that is not itself an if; the condition and the else-if links of a
   * chain add nothing.
   */
  private static int nesting(final List<SyntaxNode> ancestors, final SyntaxNode node) {
    int depth = 0;
    for (int index = 0; index < ancestors.size(); index++) {
      final SyntaxNode outer = ancestors.get(index);
      if (isIf(outer)) {
        final SyntaxNode inner = index + 1 < ancestors.size() ? ancestors.get(index + 1) : node;
        depth += deepens(outer.children(), inner);
      }
    }
    return depth;
  }

  private static int deepens(final List<SyntaxNode> parts, final SyntaxNode inner) {
    final int position = parts.indexOf(inner);
    if (position == THEN) {
      return 1;
    }
    return position > THEN && !isIf(inner) ? 1 : 0;
  }
}
