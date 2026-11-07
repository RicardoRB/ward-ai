package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports each sealed type that has no permits clause. A sealed type whose subtypes all sit in the
 * same file may leave the clause out, because the compiler then infers the list.
 */
public final class SealedShouldHavePermitsListCheck implements RuleCheck {

  private static final String SEALED = "sealed";
  private static final String PERMITS = "permits";
  private static final String BODY_CLASS = "classBody";
  private static final String BODY_INTERFACE = "interfaceBody";
  private static final String IDENTIFIER = "identifier";
  private static final String LIST_SEPARATOR = ",";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type name
   */
  public SealedShouldHavePermitsListCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      final String name = JUnitMembers.name(type);
      if (DesignMembers.modifiers(visit).contains(SEALED)
          && !hasPermits(type)
          && !subtypedInUnit(root, type, name)) {
        matches.add(CheckSupport.at(type.span(), CheckSupport.fill(message, name, "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean hasPermits(final SyntaxNode type) {
    return type.children().stream().anyMatch(child -> PERMITS.equals(SyntaxWalk.text(child)));
  }

  /** Tells whether another type of the unit names {@code name} in its extends or implements. */
  private static boolean subtypedInUnit(
      final SyntaxNode root, final SyntaxNode type, final String name) {
    return DesignMembers.types(root).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(other -> !other.equals(type))
        .anyMatch(other -> namesIn(other, name));
  }

  private static boolean namesIn(final SyntaxNode type, final String name) {
    return type.children().stream()
        .filter(child -> !IDENTIFIER.equals(child.kind()))
        .filter(child -> !BODY_CLASS.equals(child.kind()) && !BODY_INTERFACE.equals(child.kind()))
        .flatMap(child -> List.of(SyntaxWalk.text(child).split(LIST_SEPARATOR)).stream())
        .map(text -> DeclarationText.simpleName(text.trim()))
        .anyMatch(name::equals);
  }
}
