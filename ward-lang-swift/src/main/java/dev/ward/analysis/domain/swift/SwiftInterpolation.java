package dev.ward.analysis.domain.swift;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the names that the interpolations of a string literal use. The lexer keeps a literal with
 * its {@code \(...)} segments as one token, so the names in them are not nodes of the tree, and a
 * variable used only in an interpolation looked unused (#567).
 */
final class SwiftInterpolation {
  private static final String OPEN = "\\(";

  /** A name that is not a member (after a dot) and not an argument label (before a colon). */
  private static final Pattern NAME =
      Pattern.compile("(?<![.\\w$])[A-Za-z_]\\w*+(?!\\s*:)");

  private SwiftInterpolation() {}

  /**
   * Returns the names read in the interpolations of a literal, in source order.
   *
   * @param literal the text of a string literal, with its quotes
   * @return the names; empty when the literal has no interpolation
   */
  static List<String> names(final String literal) {
    final List<String> names = new ArrayList<>();
    int from = literal.indexOf(OPEN);
    while (from >= 0) {
      final int end = closing(literal, from + OPEN.length());
      final Matcher name = NAME.matcher(literal.substring(from + OPEN.length(), end));
      while (name.find()) {
        names.add(name.group());
      }
      from = literal.indexOf(OPEN, end);
    }
    return names;
  }

  /** Returns the index of the parenthesis that closes the segment that starts at {@code start}. */
  private static int closing(final String text, final int start) {
    int depth = 1;
    int index = start;
    while (index < text.length() && depth > 0) {
      final char character = text.charAt(index);
      if (character == '(') {
        depth++;
      } else if (character == ')') {
        depth--;
      }
      index++;
    }
    return depth == 0 ? index - 1 : text.length();
  }
}
