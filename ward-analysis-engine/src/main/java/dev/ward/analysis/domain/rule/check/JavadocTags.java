package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the block tags of a Javadoc comment. A block tag starts a line of the comment with an
 * {@code @}; the non-blank lines after it, up to the next block tag, continue its description.
 */
public final class JavadocTags {

  private static final String OPEN = "/**";
  private static final String EMPTY_OPEN = "/**/";
  private static final String CLOSE = "*/";
  private static final String TEXT = "text";
  private static final String AT = "@";
  private static final char ASTERISK = '*';

  private JavadocTags() {}

  /** Tells whether {@code node} is a Javadoc comment; the empty comment is not one. */
  public static boolean isJavadoc(final SyntaxNode node) {
    final String text = node.fields().getOrDefault(TEXT, "");
    return SyntaxWalk.COMMENT_KIND.equals(node.kind())
        && text.startsWith(OPEN)
        && !text.startsWith(EMPTY_OPEN);
  }

  /**
   * One block tag: its name, the position of its {@code @}, the text after the name on its line,
   * and the non-blank lines that continue it.
   *
   * @param name the tag, for example {@code @param}
   * @param span the position of the {@code @}
   * @param description the text after the name on the tag line, trimmed
   * @param continuation the continuation lines, in source order
   */
  public record Tag(String name, Span span, String description, List<Line> continuation) {

    /**
     * Copies the continuation lines.
     *
     * @throws NullPointerException if a value is missing
     */
    public Tag {
      continuation = List.copyOf(continuation);
    }
  }

  /**
   * A continuation line of a block tag.
   *
   * @param span the position of the first character of its text
   * @param indent the spaces between the leading asterisk and the text
   */
  public record Line(Span span, int indent) {}

  /** Returns the block tags of the Javadoc comment {@code comment}, in source order. */
  public static List<Tag> tags(final SyntaxNode comment) {
    final String[] rows = comment.fields().getOrDefault(TEXT, "").split("\n", -1);
    final List<Tag> tags = new ArrayList<>();
    final Builder builder = new Builder(comment, tags);
    int rowStart = 0;
    for (int index = 0; index < rows.length; index++) {
      builder.row(index, rows[index], rowStart);
      rowStart += rows[index].length() + 1;
    }
    builder.finish();
    return List.copyOf(tags);
  }

  /** Collects the tag under construction; a tag ends at the next block tag or at the end. */
  private static final class Builder {

    private final SyntaxNode comment;
    private final List<Tag> tags;
    private final List<Line> continuation = new ArrayList<>();
    private String name;
    private Span span;
    private String description;

    Builder(final SyntaxNode comment, final List<Tag> tags) {
      this.comment = comment;
      this.tags = tags;
    }

    /** Reads one row of the comment, which starts at {@code rowStart} in the comment text. */
    void row(final int index, final String row, final int rowStart) {
      final int end = row.endsWith(CLOSE) ? row.length() - CLOSE.length() : row.length();
      final int from = Math.min(index == 0 ? OPEN.length() : 0, end);
      final int lead = skipSpaces(row, from, end);
      final boolean asterisk = index > 0 && lead < end && row.charAt(lead) == ASTERISK;
      final int star = asterisk ? lead + 1 : lead;
      final int content = skipSpaces(row, star, end);
      final String text = row.substring(content, end).strip();
      if (text.isEmpty()) {
        return;
      }
      final int offset = comment.span().startOffset() + rowStart + content;
      final int line = comment.span().startLine() + index;
      final int column = index == 0 ? comment.span().startColumn() + content : content + 1;
      if (text.startsWith(AT)) {
        finish();
        startTag(text, line, column, offset);
      } else if (name != null) {
        final Span at = new Span(line, column, line, column + 1, offset, offset + 1);
        continuation.add(new Line(at, content - star));
      }
    }

    private void startTag(final String text, final int line, final int column, final int offset) {
      final int space = text.indexOf(' ');
      name = space < 0 ? text : text.substring(0, space);
      description = space < 0 ? "" : text.substring(space).strip();
      span = new Span(line, column, line, column + name.length(), offset, offset + name.length());
    }

    /** Adds the tag under construction to the tags, if there is one. */
    void finish() {
      if (name != null) {
        tags.add(new Tag(name, span, description, continuation));
        continuation.clear();
        name = null;
      }
    }

    private static int skipSpaces(final String row, final int from, final int end) {
      int position = from;
      while (position < end && Character.isWhitespace(row.charAt(position))) {
        position++;
      }
      return position;
    }
  }
}
