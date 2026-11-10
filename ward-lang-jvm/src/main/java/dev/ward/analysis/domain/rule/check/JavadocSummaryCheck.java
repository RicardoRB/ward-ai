package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reports each Javadoc comment whose summary is missing or whose first sentence does not end with
 * a period, as Checkstyle's SummaryJavadoc does. The summary is the text before the first block
 * tag; a comment that is {@code {@inheritDoc}} or starts with {@code {@return} needs no period.
 */
public final class JavadocSummaryCheck implements UnitCheck {

  private static final String INHERIT = "{@inheritDoc}";
  private static final String RETURN = "{@return";
  private static final String TAG = "@";
  private static final String INLINE = "{@";
  private static final Pattern SENTENCE_END = Pattern.compile("\\.(\\s|$)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} says what is wrong
   */
  public JavadocSummaryCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        final String summary = summary(node);
        final String problem = problem(summary);
        if (problem != null) {
          matches.add(CheckSupport.at(node.span(), CheckSupport.fill(message, problem, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static String problem(final String summary) {
    if (summary.startsWith(INHERIT) || summary.startsWith(RETURN)) {
      return null;
    }
    if (summary.isEmpty()) {
      return "the summary is missing";
    }
    return SENTENCE_END.matcher(summary).find() ? null : "the first sentence has no ending period";
  }

  private static String summary(final SyntaxNode comment) {
    final StringBuilder text = new StringBuilder();
    for (final JavadocRows.Row row : JavadocRows.of(comment)) {
      final String content = row.content();
      if (content.startsWith(TAG) && !content.startsWith(INLINE)) {
        break;
      }
      if (!content.isEmpty()) {
        text.append(text.length() == 0 ? "" : " ").append(content);
      }
    }
    return text.toString();
  }
}
