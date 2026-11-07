package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each public member of a class that is not public, since the member's visibility is
 * limited by the class and the modifier misleads readers.
 */
public final class PublicInNonPublicCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the public member
   */
  public PublicInNonPublicCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> isNonPublic(visit.ancestors()))
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> MemberDeclarations.body(clazz).stream())
        .flatMap(body -> MemberDeclarations.members(body).stream())
        .filter(member -> MemberDeclarations.modifiers(member).contains(MemberDeclarations.PUBLIC))
        .map(member -> CheckSupport.at(member.span(), message))
        .toList();
  }

  private static boolean isNonPublic(final List<SyntaxNode> ancestors) {
    return MemberDeclarations.wrapper(ancestors)
        .map(wrapper -> !MemberDeclarations.modifiers(wrapper).contains(MemberDeclarations.PUBLIC))
        .orElse(false);
  }
}
