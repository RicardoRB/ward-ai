package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks how a Javadoc comment is laid out, as Checkstyle's SingleLineJavadoc,
 * JavadocContentLocation and RequireEmptyLineBeforeBlockTagGroup do.
 */
public final class JavadocLayoutCheck implements UnitCheck {

  /** What the check looks for in each Javadoc comment. */
  public enum Mode {
    /** A comment on one line that holds a block tag. */
    SINGLE_LINE,
    /** A comment on several lines whose text starts on the opening line. */
    CONTENT_LOCATION,
    /** A first block tag that does not follow an empty row. */
    EMPTY_LINE_BEFORE_TAGS
  }

  private static final int SINGLE_ROW = 1;
  private static final int MULTI_ROW = 2;
  private static final String OPEN = "/**";
  private static final String CLOSE = "*/";
  private static final Pattern INLINE_TAG = Pattern.compile("\\{@[^}]*}");
  private static final Pattern BLOCK_TAG = Pattern.compile("@[A-Za-z]+");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what to look for
   * @param message the message of each finding
   */
  public JavadocLayoutCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        final List<JavadocRows.Row> rows = JavadocRows.of(node);
        switch (mode) {
          case SINGLE_LINE -> singleLine(rows, matches);
          case CONTENT_LOCATION -> contentLocation(rows, matches);
          default -> emptyLineBeforeTags(node, rows, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void singleLine(final List<JavadocRows.Row> rows, final List<RuleMatch> matches) {
    if (rows.size() != SINGLE_ROW) {
      return;
    }
    final JavadocRows.Row row = rows.get(0);
    final String blanked = blankInlineTags(row.text());
    final Matcher tag = BLOCK_TAG.matcher(blanked);
    if (tag.find()) {
      matches.add(CheckSupport.at(row.at(tag.start()), message));
    }
  }

  private void contentLocation(final List<JavadocRows.Row> rows, final List<RuleMatch> matches) {
    if (rows.size() < MULTI_ROW) {
      return;
    }
    final JavadocRows.Row first = rows.get(0);
    final String text = first.text();
    int position = text.indexOf(OPEN) + OPEN.length();
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
    if (position < text.length() && !text.startsWith(CLOSE, position)) {
      matches.add(CheckSupport.at(first.at(position), message));
    }
  }

  private void emptyLineBeforeTags(
      final SyntaxNode comment, final List<JavadocRows.Row> rows, final List<RuleMatch> matches) {
    final List<JavadocTags.Tag> tags = JavadocTags.tags(comment);
    if (tags.isEmpty()) {
      return;
    }
    final JavadocTags.Tag first = tags.get(0);
    final int index = first.span().startLine() - comment.span().startLine();
    if (index >= 1 && index <= rows.size() && !rows.get(index - 1).content().isEmpty()) {
      matches.add(CheckSupport.at(first.span(), message));
    }
  }

  private static String blankInlineTags(final String text) {
    final Matcher inline = INLINE_TAG.matcher(text);
    return inline.replaceAll(found -> " ".repeat(found.group().length()));
  }
}
