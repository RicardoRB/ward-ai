package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;

/**
 * Reports each return that leaves the callback of a {@code .finally(...)} call, the value of which
 * is ignored. A return inside a function nested in the callback is not reported.
 */
public final class BiomeFinallyReturnCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS =
      Set.of("arrowFunctionDeclaration", "anonymousFunction", "functionDeclaration");
  private static final String CALL = "singleExpression";
  private static final Set<String> WRAPPERS =
      Set.of(
          CALL, "anonymousFunction", "argument", "argumentList", "expressionSequence", "arguments");
  private static final String RETURN = "returnStatement";
  private static final String ARGUMENTS = "arguments";
  private static final String FINALLY_SUFFIX = ".finally";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeFinallyReturnCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(RETURN)).stream()
        .filter(visit -> inFinallyCallback(visit.ancestors()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /**
   * The nearest function is the callback of a call to {@code .finally}: only expression wrappers
   * and the argument list lie between the function and that call.
   */
  private static boolean inFinallyCallback(final List<SyntaxNode> ancestors) {
    int level = 0;
    while (level < ancestors.size() && !FUNCTIONS.contains(BiomeTree.up(ancestors, level).kind())) {
      level++;
    }
    for (int outer = level + 1; outer < ancestors.size(); outer++) {
      final SyntaxNode node = BiomeTree.up(ancestors, outer);
      if (BiomeTree.isKind(node, CALL) && BiomeTree.hasChild(node, ARGUMENTS)) {
        return isFinallyCall(node);
      }
      if (!WRAPPERS.contains(node.kind())) {
        return false;
      }
    }
    return false;
  }

  private static boolean isFinallyCall(final SyntaxNode call) {
    return BiomeTree.hasChild(call, ARGUMENTS)
        && BiomeTree.isKind(call.children().get(0), CALL)
        && SyntaxWalk.text(call.children().get(0)).endsWith(FINALLY_SUFFIX);
  }
}
