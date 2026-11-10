package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.frontend.antlr.python.Python3Lexer;

/**
 * One entry of a Python source in source order: a default-channel token, a comment, or a structural
 * marker the lexer emits.
 *
 * @param kind what the entry is
 * @param tokenType the lexer token type; unused for comments and structural markers
 * @param text the text as written; a comment loses its trailing spaces, a marker has a fixed text
 * @param start the index of the first character, or -1 for a structural marker
 * @param stop the index of the last character, or -1 for a structural marker
 * @param line the line where the entry starts, from 1
 * @param column the column where the entry starts, from 0
 * @param ownLine whether only spaces precede the entry on its line
 */
record PythonEntry(
    Kind kind,
    int tokenType,
    String text,
    int start,
    int stop,
    int line,
    int column,
    boolean ownLine) {

  /** What a Python source entry is. */
  enum Kind {
    /** A token of the default channel. */
    TOKEN,
    /** A comment, which the lexer skips. */
    COMMENT,
    /** The end of a logical line. */
    NEWLINE,
    /** The start of an indented block. */
    INDENT,
    /** The end of an indented block. */
    DEDENT
  }

  private static final String COMMENT_TYPE = "comment";

  /**
   * Returns the type name the safety checks compare.
   *
   * @return the type name
   */
  String typeName() {
    if (kind == Kind.COMMENT) {
      return COMMENT_TYPE;
    }
    if (kind == Kind.TOKEN) {
      return Python3Lexer.VOCABULARY.getDisplayName(tokenType);
    }
    return kind.name();
  }

  /**
   * Returns the last line the entry touches.
   *
   * @return the end line, from 1
   */
  int endLine() {
    return line + (int) text.chars().filter(character -> character == '\n').count();
  }
}
