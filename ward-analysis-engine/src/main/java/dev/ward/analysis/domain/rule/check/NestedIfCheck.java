package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each if statement nested at the problem depth, as PMD does: the outermost if has depth
 * one, and the statement that reaches the problem depth is reported, once per chain.
 */
public final class NestedIfCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String IF_PREFIX = "if(";
  private static final int MIN_PROBLEM_DEPTH = 1;

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
        .filter(visit -> nesting(visit.ancestors()) == problemDepth - 1)
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

  private static int nesting(final List<SyntaxNode> ancestors) {
    return (int) ancestors.stream().filter(NestedIfCheck::isIf).count();
  }
}
