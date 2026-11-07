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
 * Reports each method of a non-final class that a subclass may override, when it has no Javadoc
 * comment whose text matches the required phrase. Private, static, final and abstract methods are
 * not overridable, and neither are methods with an ignored annotation.
 */
public final class DesignForExtensionCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String METHOD = "methodDeclaration";
  private static final Set<String> NOT_OVERRIDABLE =
      Set.of("private", "static", "final", "abstract");

  private final Set<String> ignoredAnnotations;
  private final Pattern requiredJavadocPhrase;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoredAnnotations the simple names of the annotations that exempt a method
   * @param requiredJavadocPhrase the pattern the Javadoc text of an overridable method matches
   * @param message the message; {@code {value}} is the method name
   */
  public DesignForExtensionCheck(
      final Set<String> ignoredAnnotations,
      final Pattern requiredJavadocPhrase,
      final String message) {
    this.ignoredAnnotations = Set.copyOf(ignoredAnnotations);
    this.requiredJavadocPhrase =
        Objects.requireNonNull(requiredJavadocPhrase, "requiredJavadocPhrase");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      if (CLASS.equals(type.kind()) && !DesignMembers.modifiers(visit).contains("final")) {
        DesignMembers.body(type).ifPresent(body -> undocumented(body, matches));
      }
    }
    return List.copyOf(matches);
  }

  private void undocumented(final SyntaxNode body, final List<RuleMatch> matches) {
    for (final DesignMembers.Member member : DesignMembers.members(body)) {
      final SyntaxNode method = member.declaration();
      if (METHOD.equals(method.kind()) && overridable(member, method) && !documented(member)) {
        matches.add(
            CheckSupport.at(
                method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")));
      }
    }
  }

  private boolean documented(final DesignMembers.Member member) {
    return member.documented() && requiredJavadocPhrase.matcher(member.javadoc()).find();
  }

  private boolean overridable(final DesignMembers.Member member, final SyntaxNode method) {
    return NOT_OVERRIDABLE.stream().noneMatch(member::has)
        && DeclarationText.hasBody(method)
        && Collections.disjoint(member.annotations(), ignoredAnnotations);
  }
}
