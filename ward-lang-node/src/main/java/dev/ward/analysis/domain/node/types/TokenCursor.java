package dev.ward.analysis.domain.node.types;

import java.util.List;

/** A position in the tokens of a type, with the failure flag the parsers share. */
final class TokenCursor {
  private final List<String> tokens;
  private int position;
  private boolean failed;

  TokenCursor(final List<String> tokens) {
    this.tokens = tokens;
  }

  /** Returns the token {@code ahead} places from the current one, or empty text past the end. */
  String peek(final int ahead) {
    return position + ahead < tokens.size() ? tokens.get(position + ahead) : "";
  }

  boolean at(final String token) {
    return peek(0).equals(token);
  }

  boolean accept(final String token) {
    if (at(token)) {
      position++;
      return true;
    }
    return false;
  }

  void expect(final String token) {
    if (!accept(token)) {
      failed = true;
    }
  }

  /** Returns the current token and moves past it. */
  String take() {
    final String token = peek(0);
    position++;
    return token;
  }

  void skip(final int count) {
    position += count;
  }

  /** Marks the parse as failed and returns the unknown type. */
  TsType fail() {
    failed = true;
    return TsType.UNKNOWN;
  }

  boolean hasFailed() {
    return failed;
  }

  boolean isFinished() {
    return position == tokens.size();
  }

  boolean isOpen() {
    return position < tokens.size();
  }

  /** Returns the index of the matching close of the parenthesis at the current position. */
  boolean isFunctionAhead() {
    int depth = 0;
    for (int index = position; index < tokens.size(); index++) {
      depth += "(".equals(tokens.get(index)) ? 1 : 0;
      depth -= ")".equals(tokens.get(index)) ? 1 : 0;
      if (depth == 0) {
        return index + 1 < tokens.size() && "=>".equals(tokens.get(index + 1));
      }
    }
    return false;
  }

  /** Returns whether the token is a symbol rather than a name or literal. */
  static boolean isPunctuation(final String token) {
    final char first = token.isEmpty() ? ' ' : token.charAt(0);
    return token.length() == 1 && !Character.isLetterOrDigit(first) && first != '_' && first != '$';
  }
}
