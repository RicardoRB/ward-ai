package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a string annotation whose text is not an expression, like pyflakes {@code F722}. The test
 * is an approximation: the text must be non-empty, have balanced brackets, and neither start nor
 * end with an operator.
 */
public final class StringAnnotationCheck implements RuleCheck {

  private static final Set<String> HOLDERS = Set.of("tfpdef", "annassign", "funcdef");
  private static final String OPENERS = "([{";
  private static final String CLOSERS = ")]}";
  private static final String LEADING = "*/|&^%<>=,.:+-)]}";
  private static final String TRAILING = "*/|&^%<>=,.:+-([{";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public StringAnnotationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, HOLDERS)) {
      final String separator = "funcdef".equals(visit.node().kind()) ? "->" : ":";
      annotations(visit.node(), separator).stream()
          .filter(annotation -> invalid(content(SyntaxWalk.text(annotation))))
          .forEach(annotation -> found.add(new RuleMatch(annotation.span(), message)));
    }
    return List.copyOf(found);
  }

  /** The single-token string nodes that follow the separator among the node's children. */
  private static List<SyntaxNode> annotations(final SyntaxNode holder, final String separator) {
    final List<SyntaxNode> found = new ArrayList<>();
    final List<SyntaxNode> children = holder.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      final SyntaxNode next = children.get(index + 1);
      if (separator.equals(SyntaxWalk.text(children.get(index))) && quoted(next)) {
        found.add(next);
      }
    }
    return found;
  }

  private static boolean quoted(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    return SyntaxWalk.leaves(node).size() == 1
        && text.length() >= 2
        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        && text.charAt(text.length() - 1) == text.charAt(0);
  }

  private static String content(final String text) {
    return text.length() >= 2 ? text.substring(1, text.length() - 1).strip() : text;
  }

  /** Whether the text cannot be an expression, by the approximate test of the class. */
  private static boolean invalid(final String text) {
    return text.isEmpty()
        || LEADING.indexOf(text.charAt(0)) >= 0
        || TRAILING.indexOf(text.charAt(text.length() - 1)) >= 0
        || !balanced(text);
  }

  private static boolean balanced(final String text) {
    int depth = 0;
    for (final char current : text.toCharArray()) {
      if (OPENERS.indexOf(current) >= 0) {
        depth++;
      } else if (CLOSERS.indexOf(current) >= 0) {
        depth--;
      }
      if (depth < 0) {
        return false;
      }
    }
    return depth == 0;
  }
}
