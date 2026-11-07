package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a field or method annotated {@code @Inject} in a class whose constructor is also
 * annotated {@code @Inject}, as Error Prone's InjectOnMemberAndConstructor does.
 */
public final class JavaInjectMemberConstructorCheck implements RuleCheck {

  private static final String INJECT = "Inject";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaInjectMemberConstructorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      final List<JavaNodes.Member> members = JavaNodes.members(visit.node());
      final boolean constructor =
          members.stream()
              .anyMatch(
                  member ->
                      "constructorDeclaration".equals(member.node().kind()) && injected(member));
      if (constructor) {
        members.stream()
            .filter(member -> !"constructorDeclaration".equals(member.node().kind()))
            .filter(JavaInjectMemberConstructorCheck::injected)
            .forEach(member -> matches.add(CheckSupport.at(member.node().span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean injected(final JavaNodes.Member member) {
    return JUnitMembers.annotations(member.holder()).contains(INJECT);
  }
}
