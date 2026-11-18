package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;

/**
 * Reports each {@code return}, {@code throw}, {@code break} and {@code continue} that leaves a
 * {@code finally} block, like ESLint's {@code no-unsafe-finally}. A jump inside a function that is
 * written in the block leaves that function, not the block, so it is not reported. shortcut: a
 * {@code break} or {@code continue} inside a loop of the block is reported too; upgrade when a
 * corpus shows one.
 */
public final class NodeUnsafeFinallyCheck implements RuleCheck {
  private static final Set<String> JUMPS =
      Set.of("returnStatement", "throwStatement", "breakStatement", "continueStatement");
  private static final Set<String> FUNCTIONS =
      Set.of(
          "functionDeclaration",
          "generatorFunctionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "generatorMethod",
          "methodDefinition",
          "constructorDeclaration",
          "getAccessor",
          "setAccessor"
      );
  private static final String FINALLY = "finallyProduction";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeUnsafeFinallyCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, JUMPS).stream()
        .filter(visit -> leavesFinally(visit.ancestors()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** The nearest enclosing function or {@code finally} block is the block. */
  private static boolean leavesFinally(final List<SyntaxNode> ancestors) {
    for (int level = ancestors.size() - 1; level >= 0; level--) {
      final String kind = ancestors.get(level).kind();
      if (FUNCTIONS.contains(kind)) {
        return false;
      }
      if (FINALLY.equals(kind)) {
        return true;
      }
    }
    return false;
  }
}
