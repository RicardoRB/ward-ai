package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each statement or class member that starts on the line where the previous one ends, so
 * that a line holds one statement, as Checkstyle's OneStatementPerLine requires.
 */
public final class KotlinOneStatementPerLineCheck implements UnitCheck {

  private static final Set<String> LISTS = Set.of("statements", "classBody");
  private static final Set<String> ITEMS = Set.of("statement", "classMemberDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each second statement on a line
   */
  public KotlinOneStatementPerLineCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LISTS)) {
      SyntaxNode previous = null;
      for (final SyntaxNode child : visit.node().children()) {
        if (!ITEMS.contains(child.kind())) {
          continue;
        }
        if (previous != null && child.span().startLine() == previous.span().endLine()) {
          matches.add(CheckSupport.at(child.span(), message));
        }
        previous = child;
      }
    }
    return List.copyOf(matches);
  }
}
