package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each constant an interface declares, as PMD's ConstantsInInterface does with its default
 * {@code ignoreIfHasMethods}: an interface that declares a method is left alone, since its
 * constants belong to the contract of those methods.
 */
public final class ConstantsInInterfaceCheck implements UnitCheck {
  private static final Set<String> METHODS =
      Set.of("interfaceMethodDeclaration", "genericInterfaceMethodDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ConstantsInInterfaceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("interfaceBody"))) {
      final List<SyntaxNode> members = members(visit.node());
      if (members.stream().noneMatch(member -> METHODS.contains(member.kind()))) {
        members.stream()
            .filter(member -> "constDeclaration".equals(member.kind()))
            .forEach(member -> matches.add(CheckSupport.at(member.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> members(final SyntaxNode body) {
    return body.children().stream()
        .filter(declaration -> "interfaceBodyDeclaration".equals(declaration.kind()))
        .flatMap(declaration -> declaration.children().stream())
        .filter(child -> "interfaceMemberDeclaration".equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .toList();
  }
}
