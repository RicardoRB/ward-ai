package dev.ward.analysis.frontend.antlr.format.csharp;

import org.antlr.v4.runtime.Token;

/** Reads the text of C# tokens and of the source gaps between them for the formatter. */
final class CSharpTokens {
  private static final String CRLF = "\r\n";
  private static final String LF = "\n";
  private static final String LINE_COMMENT = "//";

  private CSharpTokens() {}

  /**
   * Tells whether a token is a comment or a hidden section, which the lexer puts on the hidden
   * channel.
   *
   * @param token the token
   * @return true for a comment
   */
  static boolean isComment(final Token token) {
    return token.getChannel() == Token.HIDDEN_CHANNEL;
  }

  /**
   * Returns a token's source text with line feeds only. A token's own text is not used because the
   * lexer wraps the parts of an interpolated string.
   *
   * @param codePoints the source code points
   * @param token the token
   * @return the text
   */
  static String text(final int[] codePoints, final Token token) {
    final int length = token.getStopIndex() - token.getStartIndex() + 1;
    return new String(codePoints, token.getStartIndex(), length).replace(CRLF, LF);
  }

  /**
   * Tells whether a token is a line comment, so that a line must follow it.
   *
   * @param codePoints the source code points
   * @param token the token
   * @return true for a line comment
   */
  static boolean isLineComment(final int[] codePoints, final Token token) {
    return isComment(token) && text(codePoints, token).startsWith(LINE_COMMENT);
  }

  /**
   * Returns the source between two tokens, with line feeds only. The gap before the first token
   * starts at the beginning of the source.
   *
   * @param codePoints the source code points
   * @param prev the token before the gap, or null for the start of the source
   * @param next the token after the gap, or null for the end of the source
   * @return the gap, empty when the tokens touch
   */
  static String gap(final int[] codePoints, final Token prev, final Token next) {
    final int start = prev == null ? 0 : prev.getStopIndex() + 1;
    final int end = next == null ? codePoints.length : next.getStartIndex();
    return end <= start ? "" : new String(codePoints, start, end - start).replace(CRLF, LF);
  }
}
