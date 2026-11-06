package dev.ward.application.gate;

import java.util.ArrayList;
import java.util.List;

/** Collects the problems of a rule configuration, each naming the file, the table and the key. */
final class RuleProblems {
  private final String file;
  private final List<String> messages = new ArrayList<>();

  RuleProblems(final String file, final List<String> existing) {
    this.file = file;
    messages.addAll(existing);
  }

  /** Adds {@code file: table key: detail}; {@code table} or {@code key} may be empty. */
  void add(final String table, final String key, final String detail) {
    final String where = (table.isEmpty() ? "" : " " + table) + (key.isEmpty() ? "" : " " + key);
    messages.add(file + ":" + where + (where.isEmpty() ? " " : ": ") + detail);
  }

  /** The table header of the rule {@code id}, such as {@code [rules."ward:java/x"]}. */
  static String ruleTable(final String id) {
    return "[rules.\"" + id + "\"]";
  }

  List<String> messages() {
    return List.copyOf(messages);
  }

  boolean isEmpty() {
    return messages.isEmpty();
  }
}
