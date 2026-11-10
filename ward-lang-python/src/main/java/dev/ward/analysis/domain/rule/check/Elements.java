package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;

/** Splits the text of a literal sequence or argument list into its top-level elements. */
final class Elements {

  private Elements() {}

  /**
   * Returns the elements of text such as {@code "1,2,3"} or {@code "(1,(2,3))"}, splitting at
   * commas that are not nested in brackets. A single pair of enclosing brackets is removed first,
   * and an empty text has no elements. A trailing comma does not add an element.
   */
  static List<String> of(final String text) {
    String inner = text.strip();
    if (inner.length() >= 2 && "([{".indexOf(inner.charAt(0)) >= 0 && closesAtEnd(inner)) {
      inner = inner.substring(1, inner.length() - 1);
    }
    final List<String> out = new ArrayList<>();
    int depth = 0;
    final StringBuilder current = new StringBuilder();
    for (final char c : inner.toCharArray()) {
      if ("([{".indexOf(c) >= 0) {
        depth++;
      } else if (")]}".indexOf(c) >= 0) {
        depth--;
      }
      if (c == ',' && depth == 0) {
        add(out, current);
      } else {
        current.append(c);
      }
    }
    add(out, current);
    return out;
  }

  private static void add(final List<String> out, final StringBuilder current) {
    if (!current.toString().isBlank()) {
      out.add(current.toString().strip());
    }
    current.setLength(0);
  }

  /** Reports whether the first bracket is closed by the last character. */
  private static boolean closesAtEnd(final String text) {
    int depth = 0;
    for (int index = 0; index < text.length(); index++) {
      final char c = text.charAt(index);
      if ("([{".indexOf(c) >= 0) {
        depth++;
      } else if (")]}".indexOf(c) >= 0) {
        depth--;
        if (depth == 0) {
          return index == text.length() - 1;
        }
      }
    }
    return false;
  }
}
