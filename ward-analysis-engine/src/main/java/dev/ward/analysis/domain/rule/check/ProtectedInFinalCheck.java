package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each protected member of a final class, since no subclass can use it. A method that
 * overrides a superclass method is not reported.
 */
public final class ProtectedInFinalCheck implements UnitCheck {

  private static final String OVERRIDE = "@Override";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the protected member
   */
  public ProtectedInFinalCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> isFinal(visit.ancestors()))
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> MemberDeclarations.body(clazz).stream())
        .flatMap(body -> MemberDeclarations.members(body).stream())
        .filter(ProtectedInFinalCheck::isReportedMember)
        .map(member -> CheckSupport.at(member.span(), message))
        .toList();
  }

  private static boolean isFinal(final List<SyntaxNode> ancestors) {
    return MemberDeclarations.wrapper(ancestors)
        .map(wrapper -> MemberDeclarations.modifiers(wrapper).contains(MemberDeclarations.FINAL))
        .orElse(false);
  }

  private static boolean isReportedMember(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return modifiers.contains(MemberDeclarations.PROTECTED)
        && modifiers.stream().noneMatch(OVERRIDE::equals);
  }
}
