package dev.ward.domain.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds and removes one quoted rule id in an array of the {@code [select]} table. */
final class RuleArrayEdit {
  private static final String ELEMENT_INDENT = "    ";
  private static final String SEPARATOR = ", ";
  private static final String COMMA = ",";
  private static final String OPEN = "[";

  private RuleArrayEdit() {}

  /** Removes every element equal to {@code token} from the array {@code key}, if it exists. */
  static void remove(final List<String> lines, final String key, final String token) {
    final int start = SelectTable.keyLine(lines, key);
    if (start < 0) {
      return;
    }
    // Walk backwards so a deleted line does not shift the lines still to visit.
    for (int i = SelectTable.arrayEnd(lines, start); i >= start; i--) {
      removeToken(lines, i, token);
    }
  }

  /** Adds {@code token} to the array {@code key} unless it is there; creates the key if needed. */
  static void add(final List<String> lines, final String key, final String token) {
    final int start = SelectTable.keyLine(lines, key);
    if (start < 0) {
      lines.add(SelectTable.header(lines) + 1, key + " = [" + token + "]");
      return;
    }
    final int end = SelectTable.arrayEnd(lines, start);
    if (contains(lines, start, end, token)) {
      return;
    }
    if (start == end) {
      insertInline(lines, start, token);
    } else {
      insertLine(lines, start, end, token);
    }
  }

  private static void removeToken(final List<String> lines, final int index, final String token) {
    final String line = lines.get(index);
    final int comment = TomlLines.commentStart(line);
    final String code = line.substring(0, comment);
    final Matcher match = Pattern.compile(Pattern.quote(token) + "\\s*,?\\s*").matcher(code);
    if (!match.find()) {
      return;
    }
    String before = code.substring(0, match.start());
    if (!match.group().contains(COMMA) && before.stripTrailing().endsWith(COMMA)) {
      // The removed element was the last one, so the comma before it goes too.
      final String stripped = before.stripTrailing();
      before = stripped.substring(0, stripped.length() - 1);
    }
    final String updated = before + code.substring(match.end()) + line.substring(comment);
    if (updated.isBlank()) {
      lines.remove(index);
    } else {
      lines.set(index, updated);
    }
  }

  private static void insertInline(final List<String> lines, final int index, final String token) {
    final String line = lines.get(index);
    final int close = line.lastIndexOf(']', TomlLines.commentStart(line));
    final int open = line.indexOf(OPEN);
    final String inner = line.substring(open + 1, close).trim();
    final String separator = inner.isEmpty() ? "" : inner.endsWith(COMMA) ? " " : SEPARATOR;
    lines.set(index, line.substring(0, close) + separator + token + line.substring(close));
  }

  private static void insertLine(
      final List<String> lines, final int start, final int end, final String token) {
    ensureTrailingComma(lines, start, end);
    lines.add(end, ELEMENT_INDENT + token + COMMA);
  }

  /** Gives the last element before the closing line its comma, so a new element can follow. */
  private static void ensureTrailingComma(
      final List<String> lines, final int start, final int end) {
    final int last = lastCodeLine(lines, start, end);
    if (last < 0) {
      return;
    }
    final String line = lines.get(last);
    final String code = TomlLines.codeOf(line).stripTrailing();
    if (!code.endsWith(COMMA) && !code.endsWith(OPEN)) {
      lines.set(last, code + COMMA + line.substring(code.length()));
    }
  }

  /** Returns the index of the last line before {@code end} with code, or -1. */
  private static int lastCodeLine(final List<String> lines, final int start, final int end) {
    int index = end - 1;
    while (index >= start && TomlLines.codeOf(lines.get(index)).isBlank()) {
      index--;
    }
    return index < start ? -1 : index;
  }

  private static boolean contains(
      final List<String> lines, final int start, final int end, final String token) {
    for (int i = start; i <= end; i++) {
      if (TomlLines.codeOf(lines.get(i)).contains(token)) {
        return true;
      }
    }
    return false;
  }
}
