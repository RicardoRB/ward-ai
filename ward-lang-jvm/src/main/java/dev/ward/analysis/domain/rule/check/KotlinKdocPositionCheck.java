package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/**
 * Reports each KDoc comment that is not followed by a declaration or a file header, such as a
 * comment before a statement that is not a declaration, as Checkstyle's InvalidJavadocPosition
 * reports Javadoc in such places.
 */
public final class KotlinKdocPositionCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each misplaced comment
   */
  public KotlinKdocPositionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final KotlinDocs docs = new KotlinDocs(root);
    return docs.kdocs().stream()
        .filter(comment -> !docs.precedesDeclaration(comment))
        .map(comment -> CheckSupport.at(comment.span(), message))
        .toList();
  }
}
