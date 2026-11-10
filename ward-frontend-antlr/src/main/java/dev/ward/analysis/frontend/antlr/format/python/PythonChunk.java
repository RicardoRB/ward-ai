package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.domain.format.Document;

/**
 * One piece of the laid-out source, in order: a run of blank lines, an own-line comment or a
 * logical line of code.
 *
 * @param kind what the piece is
 * @param count the number of blank lines of a blank run
 * @param depth the indentation level of a comment or a line of code
 * @param text the comment text with its indentation, for a comment
 * @param first the first token of a line of code
 * @param second the second token of a line of code, or an empty text
 * @param opensBlock whether a line of code ends with a colon
 * @param doc the document of a line of code, indentation included
 */
record PythonChunk(
    Kind kind,
    int count,
    int depth,
    String text,
    String first,
    String second,
    boolean opensBlock,
    Document doc) {

  /** The kinds of chunks. */
  enum Kind {
    /** Blank lines. */
    BLANK,
    /** An own-line comment. */
    COMMENT,
    /** A logical line of code. */
    CODE
  }

  private static final String DECORATOR = "@";
  private static final String DEF = "def";
  private static final String CLASS = "class";
  private static final String ASYNC = "async";

  /**
   * Makes a run of blank lines.
   *
   * @param count the number of blank lines
   * @return the chunk
   */
  static PythonChunk blank(final int count) {
    return new PythonChunk(Kind.BLANK, count, 0, "", "", "", false, Document.text(""));
  }

  /**
   * Makes an own-line comment.
   *
   * @param depth the indentation level
   * @param text the comment with its indentation
   * @return the chunk
   */
  static PythonChunk comment(final int depth, final String text) {
    return new PythonChunk(Kind.COMMENT, 0, depth, text, "", "", false, Document.text(""));
  }

  /**
   * Makes a logical line of code.
   *
   * @param depth the indentation level
   * @param first the first token
   * @param second the second token, or an empty text
   * @param opensBlock whether the line ends with a colon
   * @param doc the document of the line
   * @return the chunk
   */
  static PythonChunk code(
      final int depth,
      final String first,
      final String second,
      final boolean opensBlock,
      final Document doc) {
    return new PythonChunk(Kind.CODE, 0, depth, "", first, second, opensBlock, doc);
  }

  boolean isBlank() {
    return kind == Kind.BLANK;
  }

  boolean isCode() {
    return kind == Kind.CODE;
  }

  boolean isDecorator() {
    return isCode() && DECORATOR.equals(first);
  }

  boolean isClass() {
    return isCode() && CLASS.equals(first);
  }

  boolean isDef() {
    return isCode() && (DEF.equals(first) || ASYNC.equals(first) && DEF.equals(second));
  }
}
