package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code @CompileTimeConstant} parameter that the method assigns again, as Error Prone's
 * NonFinalCompileTimeConstant does.
 */
public final class JavaNonFinalConstantCheck implements RuleCheck {

  private static final Set<String> METHODS = Set.of("methodDeclaration", "constructorDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaNonFinalConstantCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("formalParameter"))) {
      final SyntaxNode parameter = visit.node();
      if (isConstant(parameter) && reassigned(parameter, visit.ancestors())) {
        matches.add(CheckSupport.at(parameter.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isConstant(final SyntaxNode parameter) {
    return JavaAnnotations.of(parameter).stream()
            .anyMatch(a -> "CompileTimeConstant".equals(JavaAnnotations.simpleName(a)))
        && !JavaAnnotations.keywords(parameter).contains("final");
  }

  private static boolean reassigned(final SyntaxNode parameter, final List<SyntaxNode> up) {
    final String name =
        JavaNodes.kid(parameter, "variableDeclaratorId").map(SyntaxWalk::text).orElse("");
    for (int i = up.size() - 1; i >= 0 && !name.isEmpty(); i--) {
      if (METHODS.contains(up.get(i).kind())) {
        return Assignments.in(up.get(i)).stream().anyMatch(a -> name.equals(Assignments.target(a)));
      }
    }
    return false;
  }
}
