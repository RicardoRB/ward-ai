package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each Javadoc comment that is not directly before a declaration, as Checkstyle's
 * InvalidJavadocPosition does: a comment in a method body, after the last member, or before an
 * import.
 */
public final class InvalidJavadocPositionCheck implements UnitCheck {

  private static final Set<String> DECLARATIONS =
      Set.of(
          "classBodyDeclaration",
          "interfaceBodyDeclaration",
          "typeDeclaration",
          "packageDeclaration",
          "enumConstant",
          "annotationTypeElementDeclaration",
          "compactConstructorDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public InvalidJavadocPositionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    matchChildren(root, matches);
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      matchChildren(node, matches);
    }
    return List.copyOf(matches);
  }

  private void matchChildren(final SyntaxNode parent, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = parent.children();
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (JavadocTags.isJavadoc(child) && !precedesDeclaration(children, index)) {
        matches.add(CheckSupport.at(child.span(), message));
      }
    }
  }

  private static boolean precedesDeclaration(final List<SyntaxNode> siblings, final int comment) {
    for (int index = comment + 1; index < siblings.size(); index++) {
      final SyntaxNode next = siblings.get(index);
      if (!SyntaxWalk.COMMENT_KIND.equals(next.kind())) {
        return DECLARATIONS.contains(next.kind());
      }
    }
    return false;
  }
}
