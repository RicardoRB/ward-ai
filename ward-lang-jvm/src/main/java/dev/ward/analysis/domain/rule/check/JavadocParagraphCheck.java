package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks the paragraphs of the description of a Javadoc comment, as Checkstyle's JavadocParagraph
 * does: a {@code <p>} tag must follow an empty row, and a paragraph that follows an empty row must
 * start with a {@code <p>} tag, unless it starts with another HTML tag.
 */
public final class JavadocParagraphCheck implements UnitCheck {

  private static final String PARAGRAPH = "<p>";
  private static final String TAG = "@";
  private static final String INLINE = "{@";
  private static final String HTML = "<";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} says what is wrong
   */
  public JavadocParagraphCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        checkComment(JavadocRows.of(node), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkComment(final List<JavadocRows.Row> rows, final List<RuleMatch> matches) {
    boolean content = !rows.get(0).content().isEmpty();
    for (int index = 1; index < rows.size(); index++) {
      final String text = rows.get(index).content();
      if (text.startsWith(TAG) && !text.startsWith(INLINE)) {
        return;
      }
      checkRow(rows, index, content, matches);
      content |= !text.isEmpty();
    }
  }

  private void checkRow(
      final List<JavadocRows.Row> rows,
      final int index,
      final boolean content,
      final List<RuleMatch> matches) {
    final JavadocRows.Row row = rows.get(index);
    final String text = row.content();
    final boolean previousEmpty = rows.get(index - 1).content().isEmpty();
    if (text.startsWith(PARAGRAPH)) {
      if (!previousEmpty) {
        report(row, text, "a paragraph tag must follow an empty line", matches);
      }
    } else if (startsParagraph(text, previousEmpty, content)) {
      report(row, text, "an empty line must be followed by a paragraph tag", matches);
    }
  }

  private static boolean startsParagraph(
      final String text, final boolean previousEmpty, final boolean content) {
    return !text.isEmpty() && !text.startsWith(HTML) && previousEmpty && content;
  }

  private void report(
      final JavadocRows.Row row,
      final String text,
      final String problem,
      final List<RuleMatch> matches) {
    matches.add(
        CheckSupport.at(row.at(row.text().indexOf(text)), CheckSupport.fill(message, problem, "")));
  }
}
