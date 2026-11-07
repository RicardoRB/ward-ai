package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports each class that has only private constructors and is neither final nor abstract. A class
 * that another class of the same file extends is left alone, because it can only be subclassed
 * there.
 */
public final class FinalClassCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the class name
   */
  public FinalClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> extended = extendedNames(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      final List<String> modifiers = DesignMembers.modifiers(visit);
      if (CLASS.equals(type.kind())
          && !modifiers.contains("final")
          && !modifiers.contains("abstract")
          && !extended.contains(JUnitMembers.name(type))
          && onlyPrivateConstructors(type)) {
        matches.add(
            CheckSupport.at(type.span(), CheckSupport.fill(message, JUnitMembers.name(type), "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean onlyPrivateConstructors(final SyntaxNode type) {
    final List<DesignMembers.Member> constructors =
        DesignMembers.body(type).stream()
            .flatMap(body -> DesignMembers.members(body).stream())
            .filter(member -> CONSTRUCTOR.equals(member.declaration().kind()))
            .toList();
    return !constructors.isEmpty()
        && constructors.stream().allMatch(constructor -> constructor.has("private"));
  }

  private static Set<String> extendedNames(final SyntaxNode root) {
    return DesignMembers.types(root).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(type -> CLASS.equals(type.kind()))
        .map(DeclarationText::superclassName)
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableSet());
  }
}
