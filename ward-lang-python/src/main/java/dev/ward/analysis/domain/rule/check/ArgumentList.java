package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;

/** Splits the comma-separated text of an argument, parameter or element list. */
final class ArgumentList {

  private ArgumentList() {}

  /** Splits at the commas outside brackets and quotes; empty items are dropped. */
  static List<String> split(final String text) {
    final List<String> items = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int depth = 0;
    char quote = 0;
    for (final char c : text.toCharArray()) {
      if (quote != 0) {
        quote = c == quote ? 0 : quote;
      } else if (c == '\'' || c == '"') {
        quote = c;
      } else if (c == '[' || c == '(' || c == '{') {
        depth++;
      } else if (c == ']' || c == ')' || c == '}') {
        depth--;
      } else if (c == ',' && depth == 0) {
        add(items, current);
        continue;
      }
      current.append(c);
    }
    add(items, current);
    return items;
  }

  /** Returns the text without its quotes when it is a quoted string, else the text itself. */
  static String unquote(final String text) {
    if (text.length() >= 2
        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        && text.charAt(text.length() - 1) == text.charAt(0)) {
      return text.substring(1, text.length() - 1);
    }
    return text;
  }

  private static void add(final List<String> items, final StringBuilder current) {
    final String item = current.toString().strip();
    if (!item.isEmpty()) {
      items.add(item);
    }
    current.setLength(0);
  }
}
