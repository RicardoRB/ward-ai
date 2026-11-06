package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The whitespace between two tokens: a space, nothing, a break, or a break inside a bracket list.
 */
final class SwiftGaps {

  private static final String COMMA = ",";
  private static final String LF = "\n";
  private static final Set<String> NO_SPACE_BEFORE = Set.of(")", "]", COMMA, ";");
  private static final Set<String> NO_SPACE_AFTER = Set.of("(", "[");
  private static final Document EMPTY = Document.concat();
  private static final Document SPACE_DOC = Document.text(" ");

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
   * @param previous the earlier token
   * @param token the later token
   * @param between the source text between them
   * @param firstInGroup true when the earlier token is the opening bracket
   * @return the gap
   */
  static Document groupGap(
      final Token previous, final Token token, final String between, final boolean firstInGroup) {
    if (firstInGroup) {
      return Document.softLine();
    }
    if (COMMA.equals(previous.getText())) {
      return Document.line();
    }
    return space(previous, token, between);
  }

  /**
   * The spacing on one line: a single space where the source had space, none before closing
   * brackets and commas, and always a space after a comma.
   *
   * @param previous the earlier token
   * @param token the later token
   * @param between the source text between them
   * @return the gap
   */
  static Document space(final Token previous, final Token token, final String between) {
    if (NO_SPACE_BEFORE.contains(token.getText()) || NO_SPACE_AFTER.contains(previous.getText())) {
      return EMPTY;
    }
    if (COMMA.equals(previous.getText()) || !between.isEmpty()) {
      return SPACE_DOC;
    }
    return EMPTY;
  }
}
