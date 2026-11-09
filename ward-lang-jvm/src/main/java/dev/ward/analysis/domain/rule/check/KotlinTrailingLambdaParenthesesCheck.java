package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports empty parentheses before a trailing lambda, as ktlint's {@code
 * unnecessary-parentheses-before-trailing-lambda} rule does: {@code run() { }} is written {@code
 * run { }}.
 *
 * <p>Shortcut: a call that follows a call ending with a lambda is reported as well.
 */
public final class KotlinTrailingLambdaParenthesesCheck implements RuleCheck {

  private static final String CALL_SUFFIX = "callSuffix";
  private static final String ARGUMENTS = "valueArguments";
  private static final String ARGUMENT = "valueArgument";
  private static final String LAMBDA = "annotatedLambda";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the empty parentheses
   */
  public KotlinTrailingLambdaParenthesesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CALL_SUFFIX))) {
      final List<SyntaxNode> children = visit.node().children();
      for (int index = 0; index + 1 < children.size(); index++) {
        if (isEmptyArguments(children.get(index))
            && LAMBDA.equals(children.get(index + 1).kind())) {
          matches.add(CheckSupport.at(children.get(index).span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isEmptyArguments(final SyntaxNode node) {
    return ARGUMENTS.equals(node.kind())
        && node.children().stream().noneMatch(child -> ARGUMENT.equals(child.kind()));
  }
}
