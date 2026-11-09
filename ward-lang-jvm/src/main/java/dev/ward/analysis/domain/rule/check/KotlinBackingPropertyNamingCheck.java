package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a Kotlin backing property, a property whose name starts with an underscore, that is not
 * named in lower camel case, is not private, or has no public property or getter of the same name
 * without the underscore in its class, as ktlint's {@code backing-property-naming} rule does. One
 * finding is reported per property.
 *
 * <p>Shortcut: a backing property of a companion object is searched in its own class body only.
 */
public final class KotlinBackingPropertyNamingCheck implements RuleCheck {

  private static final Pattern BACKING_NAME = Pattern.compile("_[a-z][a-zA-Z0-9]*");
  private static final String PROPERTY = "propertyDeclaration";
  private static final String FUN = "functionDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String IDENTIFIER_GROUP = "identifier";
  private static final String IDENTIFIER = "simpleIdentifier";
  private static final String MODIFIERS = "modifierList";
  private static final String VALUE_PARAMETERS = "functionValueParameters";
  private static final String VALUE_PARAMETER = "functionValueParameter";
  private static final String PRIVATE = "private";
  private static final String OVERRIDE = "override";
  private static final Set<String> NOT_PUBLIC = Set.of(PRIVATE, "protected", "internal");
  private static final String GET = "get";
  private static final int UNDERSCORE_LENGTH = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the name of the property
   */
  public KotlinBackingPropertyNamingCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PROPERTY))) {
      final SyntaxNode property = visit.node();
      final Optional<SyntaxNode> name = declaredName(property, VARIABLE);
      if (name.isPresent() && isBackingCandidate(property, SyntaxWalk.text(name.get()))) {
        final String text = SyntaxWalk.text(name.get());
        if (!isValidBacking(property, text, nearestClassBody(visit.ancestors()))) {
          matches.add(CheckSupport.at(name.get().span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isBackingCandidate(final SyntaxNode property, final String name) {
    return name.startsWith("_")
        && name.length() > UNDERSCORE_LENGTH
        && !hasModifier(property, OVERRIDE);
  }

  private static boolean isValidBacking(
      final SyntaxNode property, final String name, final Optional<SyntaxNode> body) {
    return BACKING_NAME.matcher(name).matches()
        && hasModifier(property, PRIVATE)
        && body.map(classBody -> hasPublicCorrelate(classBody, name.substring(UNDERSCORE_LENGTH)))
            .orElse(false);
  }

  private static boolean hasPublicCorrelate(final SyntaxNode body, final String plain) {
    final Optional<SyntaxNode> property =
        members(body, PROPERTY).stream()
            .filter(candidate -> plain.equals(declaredText(candidate, VARIABLE)))
            .findFirst();
    final Optional<SyntaxNode> correlate = property.or(() -> getter(body, GET + capitalize(plain)));
    return correlate.isPresent() && isPublic(correlate.get());
  }

  private static Optional<SyntaxNode> getter(final SyntaxNode body, final String getterName) {
    return members(body, FUN).stream()
        .filter(candidate -> getterName.equals(declaredText(candidate, IDENTIFIER_GROUP)))
        .filter(KotlinBackingPropertyNamingCheck::hasNoValueParameter)
        .findFirst();
  }

  private static boolean hasNoValueParameter(final SyntaxNode function) {
    return function.children().stream()
        .filter(child -> VALUE_PARAMETERS.equals(child.kind()))
        .noneMatch(
            parameters ->
                parameters.children().stream()
                    .anyMatch(child -> VALUE_PARAMETER.equals(child.kind())));
  }

  private static List<SyntaxNode> members(final SyntaxNode body, final String kind) {
    return body.children().stream()
        .filter(child -> MEMBER.equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .filter(declaration -> kind.equals(declaration.kind()))
        .toList();
  }

  private static Optional<SyntaxNode> nearestClassBody(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CLASS_BODY.equals(ancestors.get(index).kind())) {
        return Optional.of(ancestors.get(index));
      }
    }
    return Optional.empty();
  }

  private static Optional<SyntaxNode> declaredName(
      final SyntaxNode declaration, final String group) {
    return declaration.children().stream()
        .filter(child -> group.equals(child.kind()))
        .flatMap(child -> child.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst();
  }

  private static String declaredText(final SyntaxNode declaration, final String group) {
    return declaredName(declaration, group).map(SyntaxWalk::text).orElse("");
  }

  private static boolean isPublic(final SyntaxNode declaration) {
    return NOT_PUBLIC.stream().noneMatch(modifier -> hasModifier(declaration, modifier));
  }

  private static boolean hasModifier(final SyntaxNode declaration, final String modifier) {
    return declaration.children().stream()
        .filter(child -> MODIFIERS.equals(child.kind()))
        .flatMap(modifiers -> SyntaxWalk.leaves(modifiers).stream())
        .anyMatch(leaf -> modifier.equals(KotlinLeaves.text(leaf)));
  }

  private static String capitalize(final String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
