package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a creation outside a nested class of that class's private constructor, which needs an
 * accessor class before Java 11. Only the creations named like the class are found, so another
 * class of the same name counts.
 */
public final class AccessorClassGenerationCheck implements UnitCheck {

  private static final String CREATOR = "creator";
  private static final String IDENTIFIER = "identifier";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";

  private final int release;
  private final String message;

  /**
   * Creates the check.
   *
   * @param release the Java release, or zero when unknown
   * @param message the message; {@code {value}} is the class name
   */
  public AccessorClassGenerationCheck(final int release, final String message) {
    this.release = release;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (!AccessorRelease.applies(release)) {
      return List.of();
    }
    final List<RuleMatch> matches = new ArrayList<>();
    final List<SyntaxNode> creators =
        SyntaxWalk.descendants(root).stream().filter(node -> CREATOR.equals(node.kind())).toList();
    for (final SyntaxNode nested : AccessorRelease.nestedClasses(root)) {
      if (hasPrivateConstructor(nested)) {
        final String name = JUnitMembers.className(nested);
        final Set<SyntaxNode> inside = Set.copyOf(SyntaxWalk.descendants(nested));
        creators.stream()
            .filter(creator -> name.equals(createdName(creator)) && !inside.contains(creator))
            .forEach(
                creator ->
                    matches.add(
                        CheckSupport.at(creator.span(), CheckSupport.fill(message, name, ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean hasPrivateConstructor(final SyntaxNode nested) {
    return JUnitMembers.body(nested).stream()
        .flatMap(body -> JUnitMembers.members(body).stream())
        .filter(JUnitMembers::isPrivate)
        .anyMatch(
            member ->
                member.children().stream()
                    .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
                    .flatMap(child -> child.children().stream())
                    .anyMatch(child -> CONSTRUCTOR.equals(child.kind())));
  }

  private static String createdName(final SyntaxNode creator) {
    return SyntaxWalk.descendants(creator).stream()
        .filter(node -> IDENTIFIER.equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
