package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each statement that starts on the line where the previous statement of the same list
 * ends, so that a line holds one statement, as Checkstyle's OneStatementPerLine requires. It reads
 * the syntax tree, so a semicolon in a comment, a string or a type is not a statement.
 */
public final class NodeOneStatementPerLineCheck implements RuleCheck {
  private static final Set<String> LISTS = Set.of("statementList", "sourceElements");
  private static final Set<String> ITEMS = Set.of("statement", "sourceElement");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each second statement on a line
   */
  public NodeOneStatementPerLineCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (LISTS.contains(node.kind())) {
      SyntaxNode previous = null;
      for (final SyntaxNode child : node.children()) {
        if (!ITEMS.contains(child.kind())) {
          continue;
        }
        if (previous != null && child.span().startLine() == previous.span().endLine()) {
          matches.add(new RuleMatch(child.span(), message));
        }
        previous = child;
      }
    }
    node.children().forEach(child -> collect(child, matches));
  }
}
