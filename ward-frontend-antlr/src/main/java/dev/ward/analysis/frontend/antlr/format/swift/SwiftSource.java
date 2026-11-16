package dev.ward.analysis.frontend.antlr.format.swift;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/** Reads the source text around tokens from their character stream. */
final class SwiftSource {
  private SwiftSource() {}

  /**
   * The source text between two tokens. It is read from the character stream of the tokens: their
   * offsets count code points, so they do not index a {@code String} after a character outside the
   * Basic Multilingual Plane, such as an emoji.
   *
   * @param previous the earlier token
   * @param token the later token
   * @return the characters between them
   */
  static String between(final Token previous, final Token token) {
    return text(previous.getInputStream(), previous.getStopIndex() + 1, token.getStartIndex() - 1);
  }

  /**
   * The source text after a token.
   *
   * @param last the token
   * @return the characters from the end of the token to the end of the source
   */
  static String after(final Token last) {
    final CharStream stream = last.getInputStream();
    return text(stream, last.getStopIndex() + 1, stream.size() - 1);
  }

  private static String text(final CharStream stream, final int first, final int last) {
    return first > last ? "" : stream.getText(Interval.of(first, last));
  }
}
