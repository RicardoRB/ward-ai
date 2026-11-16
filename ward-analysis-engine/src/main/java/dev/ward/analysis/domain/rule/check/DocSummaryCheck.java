package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reports a documentation comment whose first paragraph has no sentence that ends with a period, a
 * question mark or an exclamation mark. The paragraph is the lines up to the first empty line, list
 * item or callout; the lines of one {@code ///} run are read together, so a sentence may end on a
 * later line and the lines after the first paragraph are not checked.
 */
public final class DocSummaryCheck implements RuleCheck {
  private static final Pattern SENTENCE_END = Pattern.compile("[.!?]([\\s`*_)\"']|$)");
  private static final Pattern PARAGRAPH_END = Pattern.compile("^([-+>#]|\\* ).*");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of a finding
   */
  public DocSummaryCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    final List<SyntaxNode> run = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (DocComments.isDoc(child)) {
        run.add(child);
      } else {
        check(run, matches);
        run.clear();
        collect(child, matches);
      }
    }
    check(run, matches);
  }

  private void check(final List<SyntaxNode> run, final List<RuleMatch> matches) {
    if (run.isEmpty()) {
      return;
    }
    final String summary = summary(DocComments.lines(run));
    if (!summary.isEmpty() && !SENTENCE_END.matcher(summary).find()) {
      matches.add(new RuleMatch(run.get(0).span(), message));
    }
  }

  /** Returns the first paragraph of the lines, joined by spaces. */
  private static String summary(final List<String> lines) {
    final StringBuilder summary = new StringBuilder();
    for (final String line : lines) {
      final boolean ends = line.isEmpty() || PARAGRAPH_END.matcher(line).matches();
      if (ends && !summary.isEmpty()) {
        break;
      }
      if (!ends) {
        summary.append(line).append(' ');
      }
    }
    return summary.toString().strip();
  }
}
