package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The whitespace between two tokens: a space, nothing, a break, or a break inside a bracket list.
 * Spacing follows token pairs: one space around binary and assignment operators, after commas,
 * colons, keywords and before {@code {}; none inside brackets, around member dots, before a call
 * or subscript bracket, or after a prefix operator. Other spacing is kept as written.
 */
final class SwiftGaps {

  private static final String COMMA = ",";
  private static final String COLON = ":";
  private static final String DOT = ".";
  private static final String OPEN_BRACE = "{";
  private static final String LF = "\n";
  private static final Set<String> NO_SPACE_BEFORE = Set.of(")", "]", COMMA, ";");
  private static final Set<String> NO_SPACE_AFTER = Set.of("(", "[");
  private static final Set<String> CALL_OPENERS = Set.of("(", "[");
  private static final Document EMPTY = Document.concat();
  private static final Document SPACE_DOC = Document.text(" ");

  /** The spacing decided for one gap. */
  private enum Spacing {
    SPACE,
    NONE,
    KEEP
  }

  private SwiftGaps() {}

  /**
   * The source text between two tokens.
   *
   * @param source the whole source
   * @param previous the earlier token
   * @param token the later token
   * @return the characters between them
   */
  static String between(final String source, final Token previous, final Token token) {
    return source.substring(previous.getStopIndex() + 1, token.getStartIndex());
  }

  /**
   * Counts the line feeds in a text.
   *
   * @param text the text
   * @return the number of line feeds
   */
  static int countNewlines(final String text) {
    return (int) text.chars().filter(character -> character == '\n').count();
  }

  /**
   * The break for a gap with source line breaks: blank lines are kept up to the maximum.
   *
   * @param newlines the line feeds in the gap, at least one
   * @param maxBlankLines the most blank lines kept in a row
   * @return the break
   */
  static Document newlines(final int newlines, final int maxBlankLines) {
    final int blank = Math.min(newlines - 1, maxBlankLines);
    final List<Document> parts = new ArrayList<>();
    for (int line = 0; line < blank; line++) {
      parts.add(Document.text(LF));
    }
    parts.add(Document.hardLine());
    return new Document.Concat(parts);
  }

  /**
   * The gap inside a bracket list that may break: a break after the opening bracket and after each
   * top-level comma, otherwise the same spacing as elsewhere.
   *
   * @param before the token before the earlier one, or null
   * @param previous the earlier token
   * @param token the later token
   * @param between the source text between them
   * @param firstInGroup true when the earlier token is the opening bracket
   * @return the gap
   */
  static Document groupGap(
      final Token before,
      final Token previous,
      final Token token,
      final String between,
      final boolean firstInGroup) {
    if (firstInGroup) {
      return Document.softLine();
    }
    if (COMMA.equals(previous.getText())) {
      return Document.line();
    }
    return space(before, previous, token, between);
  }

  /**
   * The spacing on one line, decided by the token pair.
   *
   * @param before the token before the earlier one, or null
   * @param previous the earlier token
   * @param token the later token
   * @param between the source text between them
   * @return the gap
   */
  static Document space(
      final Token before, final Token previous, final Token token, final String between) {
    return switch (spacing(before, previous, token, between)) {
      case SPACE -> SPACE_DOC;
      case NONE -> EMPTY;
      case KEEP -> between.isEmpty() ? EMPTY : SPACE_DOC;
    };
  }

  private static Spacing spacing(
      final Token before, final Token previous, final Token token, final String between) {
    final Spacing closing = closing(before, previous, token, between);
    if (closing != null) {
      return closing;
    }
    final Spacing opening = opening(previous, token);
    return opening == null ? operator(before, previous, token) : opening;
  }

  /** Spacing that depends on closing brackets, member dots, calls, subscripts and postfix marks. */
  private static Spacing closing(
      final Token before, final Token previous, final Token token, final String between) {
    final String prev = previous.getText();
    final String text = token.getText();
    if ((between.isEmpty() && SwiftTokens.isGluedOperator(previous, text))
        || NO_SPACE_BEFORE.contains(text)
        || NO_SPACE_AFTER.contains(prev)
        || SwiftTokens.isPrefix(before, previous)) {
      return Spacing.NONE;
    }
    return access(previous, token, between);
  }

  /** Spacing around member dots, calls, subscripts and postfix marks; null when none applies. */
  private static Spacing access(final Token previous, final Token token, final String between) {
    final String prev = previous.getText();
    final String text = token.getText();
    if (DOT.equals(text)) {
      return SwiftTokens.isOperandEnd(previous) ? Spacing.NONE : Spacing.SPACE;
    }
    if (DOT.equals(prev)) {
      return Spacing.NONE;
    }
    if (CALL_OPENERS.contains(text)) {
      return SwiftTokens.isOperandEnd(previous) ? Spacing.NONE : Spacing.SPACE;
    }
    if (SwiftTokens.POSTFIX.contains(text)) {
      return SwiftTokens.isOperandEnd(previous) && between.isEmpty() ? Spacing.NONE : Spacing.SPACE;
    }
    return null;
  }

  /** Spacing after a comma, colon, keyword or postfix mark, and before a block. */
  private static Spacing opening(final Token previous, final Token token) {
    final String prev = previous.getText();
    if (COMMA.equals(prev)
        || COLON.equals(prev)
        || SwiftTokens.POSTFIX.contains(prev)
        || SwiftTokens.KEYWORDS.contains(prev)
        || OPEN_BRACE.equals(token.getText())) {
      return Spacing.SPACE;
    }
    return null;
  }

  /** One space around a binary or assignment operator; anything else is kept as written. */
  private static Spacing operator(final Token before, final Token previous, final Token token) {
    if (SwiftTokens.isBinaryAt(previous, token.getText())
        || SwiftTokens.endsBinary(before, previous)) {
      return Spacing.SPACE;
    }
    return Spacing.KEEP;
  }
}
