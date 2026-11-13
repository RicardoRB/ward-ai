package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;

/** The line-level moves of the parser: blanks, comments, line breaks, indentation and positions. */
final class YamlLines {

  private static final char TAB = '\t';
  private static final char COMMENT = '#';
  private static final String DOCUMENT_START = "---";
  private static final String DOCUMENT_END = "...";

  private final YamlSource source;
  private final YamlCursor cursor;

  YamlLines(final YamlSource source, final YamlCursor cursor) {
    this.source = source;
    this.cursor = cursor;
  }

  Span span(final int from, final int to) {
    return source.span(from, to);
  }

  int line(final int offset) {
    return source.line(offset);
  }

  int column(final int offset) {
    return source.column(offset);
  }

  /** The number of characters between the start of the line and the cursor. */
  int indent() {
    return cursor.pos() - source.lineStart(cursor.pos());
  }

  boolean atLineEnd() {
    return cursor.eof() || YamlCursor.isBreak(cursor.peek());
  }

  void skipSpaces() {
    while (cursor.peek() == ' ' || cursor.peek() == TAB) {
      cursor.advance(1);
    }
  }

  /** Moves past the spaces and an optional comment and tells whether the line is then over. */
  boolean skipToLineEnd() {
    skipSpaces();
    if (cursor.peek() == COMMENT && (cursor.pos() == 0 || cursor.isBlankAt(cursor.pos() - 1))) {
      while (!atLineEnd()) {
        cursor.advance(1);
      }
    }
    return atLineEnd();
  }

  /** Moves past the line break at the cursor, if any. */
  void skipBreak() {
    if (cursor.peek() == '\r' && cursor.charAt(cursor.pos() + 1) == '\n') {
      cursor.advance(2);
    } else if (YamlCursor.isBreak(cursor.peek())) {
      cursor.advance(1);
    }
  }

  /**
   * Moves to the first character of the next line with content, past blank lines, comments and the
   * indentation. A tab in that indentation is a syntax error.
   */
  void skipBlank() {
    while (!cursor.eof()) {
      skipToLineEnd();
      if (!YamlCursor.isBreak(cursor.peek())) {
        break;
      }
      skipBreak();
    }
    if (!cursor.eof()) {
      for (int index = source.lineStart(cursor.pos()); index < cursor.pos(); index++) {
        if (cursor.text().charAt(index) == TAB) {
          throw cursor.fail("tab characters cannot indent a line", index);
        }
      }
    }
  }

  /** Tells whether a document marker ({@code ---} or {@code ...}) starts the line at the cursor. */
  boolean atDocumentMarker() {
    final String text = cursor.text();
    final int pos = cursor.pos();
    return indent() == 0
        && (text.startsWith(DOCUMENT_START, pos) || text.startsWith(DOCUMENT_END, pos))
        && cursor.isBlankAt(pos + DOCUMENT_START.length());
  }
}
