package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each property declaration that starts on the line where an earlier declaration of the
 * same statement list or class body starts, so that a line declares one variable, as Checkstyle's
 * MultipleVariableDeclarations requires. A destructuring declaration counts as one declaration.
 */
public final class KotlinMultipleDeclarationsCheck implements UnitCheck {

  private static final Set<String> LISTS = Set.of("statements", "classBody");
  private static final String STATEMENT = "statement";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String DECLARATION = "declaration";
  private static final String PROPERTY = "propertyDeclaration";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each declaration after the first on a line
   */
  public KotlinMultipleDeclarationsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LISTS)) {
      checkList(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkList(final SyntaxNode list, final List<RuleMatch> matches) {
    int previousLine = -1;
    for (final SyntaxNode declaration : declarationsOf(list)) {
      final int line = declaration.span().startLine();
      if (line == previousLine) {
        matches.add(CheckSupport.at(declaration.span(), message));
      }
      previousLine = line;
    }
  }

  /** Returns the property declarations of a statement list or class body, in source order. */
  private static List<SyntaxNode> declarationsOf(final SyntaxNode list) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode item : list.children()) {
      if (STATEMENT.equals(item.kind())) {
        KotlinDocs.childrenOf(item, DECLARATION)
            .forEach(declaration -> found.addAll(KotlinDocs.childrenOf(declaration, PROPERTY)));
      } else if (MEMBER.equals(item.kind())) {
        found.addAll(KotlinDocs.childrenOf(item, PROPERTY));
      }
    }
    return found;
  }
}
