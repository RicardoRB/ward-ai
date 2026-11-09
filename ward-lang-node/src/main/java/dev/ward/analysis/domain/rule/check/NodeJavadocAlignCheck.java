package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reports each javadoc comment whose leading asterisks are not one column right of the opening
 * slash, like Checkstyle's JavadocLeadingAsteriskAlign. The columns come from the comment's span
 * and its raw source lines; the finding is placed at the comment.
 */
public final class NodeJavadocAlignCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String JAVADOC = "/**";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeJavadocAlignCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (COMMENT.equals(node.kind())) {
      misaligned(node).ifPresent(found::add);
    }
    node.children().forEach(child -> collect(child, found));
  }

  private Optional<RuleMatch> misaligned(final SyntaxNode comment) {
    final String text = comment.fields().getOrDefault("text", "");
    if (!text.startsWith(JAVADOC) || !text.contains("\n")) {
      return Optional.empty();
    }
    final Span span = comment.span();
    final boolean off =
        Arrays.stream(text.split("\n", -1)).skip(1).anyMatch(line -> misalignedLine(line, span));
    return off ? Optional.of(new RuleMatch(span, message)) : Optional.empty();
  }

  /** Whether the line starts with an asterisk that is not at the column after the slash. */
  private static boolean misalignedLine(final String line, final Span comment) {
    final int star = line.indexOf('*');
    return star >= 0 && line.substring(0, star).isBlank() && star != comment.startColumn();
  }
}
