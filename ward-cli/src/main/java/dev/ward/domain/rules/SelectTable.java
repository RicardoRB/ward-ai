package dev.ward.domain.rules;

import java.util.List;
import java.util.regex.Pattern;

/** Finds the {@code [select]} table and the arrays inside it in the lines of a TOML file. */
final class SelectTable {
  /** The header line of the table. */
  static final String HEADER = "[select]";

  private SelectTable() {}

  /** Adds the table at the end of {@code lines} when the file has none. */
  static void ensure(final List<String> lines) {
    if (header(lines) >= 0) {
      return;
    }
    if (!lines.get(lines.size() - 1).isBlank()) {
      lines.add("");
    }
    lines.add(HEADER);
    lines.add("");
  }

  /** Returns the index of the header line, or -1. */
  static int header(final List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      if (HEADER.equals(lines.get(i).trim())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the line of {@code key = ...} inside the table, or -1 when the table has no such key.
   *
   * @throws IllegalArgumentException if the key is set to something other than an array
   */
  static int keyLine(final List<String> lines, final String key) {
    final Pattern assignment = Pattern.compile("^\\s*" + key + "\\s*=");
    for (int i = header(lines) + 1; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (line.trim().startsWith("[")) {
        return -1;
      }
      if (assignment.matcher(line).find()) {
        return requireArray(line, key, i);
      }
    }
    return -1;
  }

  /** Returns the line that closes the array that starts at {@code start}. */
  static int arrayEnd(final List<String> lines, final int start) {
    int depth = 0;
    for (int i = start; i < lines.size(); i++) {
      depth += TomlLines.depthChange(lines.get(i));
      if (depth <= 0) {
        return i;
      }
    }
    throw new IllegalArgumentException("[select] array is not closed");
  }

  private static int requireArray(final String line, final String key, final int index) {
    if (TomlLines.codeOf(line).indexOf('[') < 0) {
      throw new IllegalArgumentException("[select] " + key + " must be an array of strings");
    }
    return index;
  }
}
