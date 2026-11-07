package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each field of a class that is not private, unless the options allow its visibility.
 * Static final constants are never checked, and neither are fields that match the public member
 * pattern or carry an ignored annotation.
 */
public final class VisibilityModifierCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String FIELD = "fieldDeclaration";
  private static final String PUBLIC = "public";
  private static final String PROTECTED = "protected";
  private static final String PRIVATE = "private";
  private static final String STATIC = "static";
  private static final String FINAL = "final";

  private final FieldVisibility visibility;
  private final String message;

  /**
   * Creates the check.
   *
   * @param visibility the options that allow field visibilities
   * @param message the message; {@code {value}} is the field name
   */
  public VisibilityModifierCheck(final FieldVisibility visibility, final String message) {
    this.visibility = Objects.requireNonNull(visibility, "visibility");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      if (CLASS.equals(type.kind())) {
        DesignMembers.body(type).ifPresent(body -> violations(body, matches));
      }
    }
    return List.copyOf(matches);
  }

  private void violations(final SyntaxNode body, final List<RuleMatch> matches) {
    for (final DesignMembers.Member member : DesignMembers.members(body)) {
      final SyntaxNode field = member.declaration();
      if (FIELD.equals(field.kind()) && !isConstant(member) && !isExempt(member)) {
        for (final String name : DeclarationText.declaredNames(field)) {
          if (!allowed(member, field) && !visibility.publicMemberPattern().matcher(name).find()) {
            matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, name, "")));
          }
        }
      }
    }
  }

  private static boolean isConstant(final DesignMembers.Member member) {
    return member.has(STATIC) && member.has(FINAL);
  }

  private boolean isExempt(final DesignMembers.Member member) {
    return !Collections.disjoint(member.annotations(), visibility.ignoredAnnotations());
  }

  private boolean allowed(final DesignMembers.Member member, final SyntaxNode field) {
    if (member.has(PRIVATE)) {
      return true;
    }
    if (member.has(PUBLIC)) {
      return (visibility.allowPublicFinalFields() && member.has(FINAL))
          || (visibility.allowPublicImmutableFields() && isImmutable(field));
    }
    if (member.has(PROTECTED)) {
      return visibility.protectedAllowed();
    }
    return visibility.packageAllowed();
  }

  private boolean isImmutable(final SyntaxNode field) {
    final String type = DeclarationText.simpleTypeText(field);
    return !type.contains("[") && visibility.immutableTypes().contains(type);
  }

  /**
   * The options that decide which field visibilities are allowed.
   *
   * @param allowPublicFinalFields whether a public final field is allowed
   * @param allowPublicImmutableFields whether a public field of an immutable type is allowed
   * @param packageAllowed whether a package-private field is allowed
   * @param protectedAllowed whether a protected field is allowed
   * @param publicMemberPattern the names of fields that are always allowed
   * @param ignoredAnnotations the simple names of the annotations that exempt a field
   * @param immutableTypes the simple names of the types that count as immutable
   */
  public record FieldVisibility(
      boolean allowPublicFinalFields,
      boolean allowPublicImmutableFields,
      boolean packageAllowed,
      boolean protectedAllowed,
      Pattern publicMemberPattern,
      Set<String> ignoredAnnotations,
      Set<String> immutableTypes) {

    /**
     * Copies the sets and checks the pattern.
     *
     * @throws NullPointerException if a value is missing
     */
    public FieldVisibility {
      Objects.requireNonNull(publicMemberPattern, "publicMemberPattern");
      ignoredAnnotations = Set.copyOf(ignoredAnnotations);
      immutableTypes = Set.copyOf(immutableTypes);
    }
  }
}
