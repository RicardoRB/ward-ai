package dev.ward.analysis.frontend.antlr.format.ecma;

/**
 * One code token, a comment or a template literal, with the whitespace that came before it. A
 * template literal is one token whose text is its exact source, so its spacing never changes.
 *
 * @param text the token text exactly as written
 * @param line the line where the token starts, from 1
 * @param kind whether the token is code or a comment
 * @param spaceBefore whether whitespace separated it from the token before
 */
record EcmaToken(String text, int line, Kind kind, boolean spaceBefore) {

  /** The kinds of token the formatter sees. */
  enum Kind {
    /** A token of the program. */
    CODE,
    /** A comment that ends its line. */
    LINE_COMMENT,
    /** A comment that may span lines. */
    BLOCK_COMMENT
  }

  /**
   * Returns the line where the token ends.
   *
   * @return the last line the token touches
   */
  int endLine() {
    return line + (int) text.chars().filter(c -> c == '\n').count();
  }

  /**
   * Tells whether the token is a comment.
   *
   * @return true for a line or block comment
   */
  boolean comment() {
    return kind != Kind.CODE;
  }
}
