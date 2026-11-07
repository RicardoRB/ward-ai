package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each class that holds only static methods and fields, and whose constructor is not
 * private: a utility class has to hide its constructor. A class annotated with one of the ignored
 * annotations is left alone.
 */
public final class HideUtilityClassConstructorCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String METHOD = "methodDeclaration";
  private static final String FIELD = "fieldDeclaration";

  private final Set<String> ignoredAnnotations;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoredAnnotations the simple names of the annotations that exempt a class
   * @param message the message; {@code {value}} is the class name
   */
  public HideUtilityClassConstructorCheck(
      final Set<String> ignoredAnnotations, final String message) {
    this.ignoredAnnotations = Set.copyOf(ignoredAnnotations);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      if (CLASS.equals(type.kind())
          && Collections.disjoint(
              DeclarationText.annotationNames(DesignMembers.modifiers(visit)), ignoredAnnotations)
          && isUtility(type)) {
        matches.add(
            CheckSupport.at(type.span(), CheckSupport.fill(message, JUnitMembers.name(type), "")));
      }
    }
    return List.copyOf(matches);
  }

  /**
   * Tells whether a class has static methods or fields and no instance ones, and no private one.
   */
  private static boolean isUtility(final SyntaxNode type) {
    final List<DesignMembers.Member> members =
        DesignMembers.body(type).map(DesignMembers::members).orElse(List.of());
    final List<DesignMembers.Member> code =
        members.stream().filter(HideUtilityClassConstructorCheck::isMethodOrField).toList();
    final boolean hasStatic = code.stream().anyMatch(member -> member.has("static"));
    final boolean hasInstance = code.stream().anyMatch(member -> !member.has("static"));
    final List<DesignMembers.Member> constructors =
        members.stream().filter(member -> CONSTRUCTOR.equals(member.declaration().kind())).toList();
    return hasStatic
        && !hasInstance
        && (constructors.isEmpty()
            || constructors.stream().anyMatch(constructor -> !constructor.has("private")));
  }

  private static boolean isMethodOrField(final DesignMembers.Member member) {
    final String kind = member.declaration().kind();
    return METHOD.equals(kind) || FIELD.equals(kind);
  }
}
