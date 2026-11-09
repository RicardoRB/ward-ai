package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each public class, interface or object that has no KDoc comment, as Checkstyle's
 * MissingJavadocType does for the public scope. Kotlin declarations are public unless a visibility
 * modifier says otherwise.
 */
public final class KotlinMissingKdocTypeCheck implements UnitCheck {

  private static final Set<String> TYPES = Set.of("classDeclaration", "objectDeclaration");
  private static final Set<String> NOT_PUBLIC = Set.of("private", "protected", "internal");
  private static final String MODIFIERS = "modifierList";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each undocumented type
   */
  public KotlinMissingKdocTypeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final KotlinDocs docs = new KotlinDocs(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TYPES)) {
      final SyntaxNode type = visit.node();
      if (isPublic(type) && docs.documenting(type).isEmpty()) {
        matches.add(CheckSupport.at(type.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isPublic(final SyntaxNode type) {
    return KotlinDocs.childrenOf(type, MODIFIERS).stream()
        .flatMap(modifiers -> SyntaxWalk.leaves(modifiers).stream())
        .map(KotlinDocs::text)
        .noneMatch(NOT_PUBLIC::contains);
  }
}
