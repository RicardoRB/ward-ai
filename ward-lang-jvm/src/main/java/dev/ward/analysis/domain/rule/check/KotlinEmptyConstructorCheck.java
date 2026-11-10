package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a primary constructor that takes no arguments and has no annotation or visibility
 * modifier, as detekt's {@code empty-blocks} does. It skips expect and actual classes, and a class
 * whose secondary constructor calls {@code this()}, because removing the primary constructor would
 * break that call.
 */
public final class KotlinEmptyConstructorCheck implements RuleCheck {

  private static final String PRIMARY = "primaryConstructor";
  private static final String CLASS = "classDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String SECONDARY = "secondaryConstructor";
  private static final String DELEGATION = "constructorDelegationCall";
  private static final String MODIFIERS = "modifierList";
  private static final Set<String> MULTIPLATFORM = Set.of("expect", "actual");
  private static final Pattern BARE = Pattern.compile("^(constructor\\s*)?\\(\\s*\\)$");
  private static final Pattern THIS_CALL = Pattern.compile("this\\s*\\(\\s*\\)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the constructor
   */
  public KotlinEmptyConstructorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PRIMARY))) {
      final List<SyntaxNode> ancestors = visit.ancestors();
      if (!ancestors.isEmpty()
          && CLASS.equals(ancestors.get(ancestors.size() - 1).kind())
          && isRemovable(visit.node(), ancestors.get(ancestors.size() - 1))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isRemovable(final SyntaxNode constructor, final SyntaxNode clazz) {
    return BARE.matcher(SyntaxWalk.text(constructor)).matches()
        && !isMultiplatform(clazz)
        && !callsThis(clazz);
  }

  private static boolean isMultiplatform(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(child -> MODIFIERS.equals(child.kind()))
        .flatMap(modifiers -> SyntaxWalk.leaves(modifiers).stream())
        .anyMatch(leaf -> MULTIPLATFORM.contains(leaf.fields().get("text")));
  }

  private static boolean callsThis(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()))
        .flatMap(body -> body.children().stream())
        .filter(member -> MEMBER.equals(member.kind()))
        .flatMap(member -> member.children().stream())
        .filter(child -> SECONDARY.equals(child.kind()))
        .flatMap(secondary -> secondary.children().stream())
        .filter(child -> DELEGATION.equals(child.kind()))
        .anyMatch(call -> THIS_CALL.matcher(SyntaxWalk.text(call)).find());
  }
}
