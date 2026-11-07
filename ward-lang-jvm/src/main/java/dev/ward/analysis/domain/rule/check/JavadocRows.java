package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** The rows of a Javadoc comment, each with the position where it starts. */
final class JavadocRows {

  private static final String TEXT = "text";
  private static final String OPEN = "/**";
  private static final String CLOSE = "*/";

  private JavadocRows() {}

  /**
   * One row of a comment.
   *
   * @param text the row without its line break
   * @param index the row's number in the comment, from 0
   * @param line the line of the row in the file
   * @param column the column where the row starts
   * @param offset the file offset where the row starts
   */
  record Row(String text, int index, int line, int column, int offset) {

    /** Returns the span of the character at {@code position} in this row. */
    Span at(final int position) {
      return new Span(
          line,
          column + position,
          line,
          column + position + 1,
          offset + position,
          offset + position + 1);
    }

    /** Returns the position of the first character that is not a space, or the row length. */
    int lead() {
      int position = 0;
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
        position++;
      }
      return position;
    }

    /** Returns the row's words, without the comment delimiters or a leading asterisk. */
    String content() {
      String body = index == 0 && text.startsWith(OPEN) ? text.substring(OPEN.length()) : text;
      body = body.strip();
      if (body.endsWith(CLOSE)) {
        body = body.substring(0, body.length() - CLOSE.length()).strip();
      }
      while (index > 0 && body.startsWith("*")) {
        body = body.substring(1).strip();
      }
      return body;
    }
  }

  /** Returns the rows of the Javadoc comment {@code comment}, in source order. */
  static List<Row> of(final SyntaxNode comment) {
    final String[] lines = comment.fields().getOrDefault(TEXT, "").split("\n", -1);
    final List<Row> rows = new ArrayList<>();
    int offset = comment.span().startOffset();
    for (int index = 0; index < lines.length; index++) {
      final int column = index == 0 ? comment.span().startColumn() : 1;
      rows.add(new Row(lines[index], index, comment.span().startLine() + index, column, offset));
      offset += lines[index].length() + 1;
    }
    return List.copyOf(rows);
  }
}
