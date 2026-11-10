package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method annotated {@code @InlineMe} whose body is not a single statement, as Error
 * Prone's InlineMeValidator does for the plain case: a caller can only be rewritten to the body
 * when the body is one statement.
 */
public final class JavaInlineMeCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaInlineMeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(
            root, Set.of("classDeclaration", "enumDeclaration", "recordDeclaration"))) {
      JavaNodes.members(visit.node()).stream()
          .filter(member -> "methodDeclaration".equals(member.node().kind()))
          .filter(JavaInlineMeCheck::inlineMe)
          .filter(member -> statements(member.node()) > 1)
          .forEach(
              member ->
                  matches.add(
                      CheckSupport.at(
                          member.node().span(),
                          CheckSupport.fill(message, JavaNodes.name(member.node()), ""))));
    }
    return List.copyOf(matches);
  }

  private static boolean inlineMe(final JavaNodes.Member member) {
    return JavaAnnotations.of(member.holder()).stream()
        .anyMatch(annotation -> "InlineMe".equals(JavaAnnotations.simpleName(annotation)));
  }

  private static int statements(final SyntaxNode method) {
    return JavaNodes.kid(method, "methodBody")
        .flatMap(body -> JavaNodes.kid(body, "block"))
        .map(block -> JavaNodes.statements(block).size())
        .orElse(0);
  }
}
