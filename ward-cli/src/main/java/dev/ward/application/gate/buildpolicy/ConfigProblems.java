package dev.ward.application.gate.buildpolicy;

import java.util.ArrayList;
import java.util.List;

/** Collects the problems of a rule configuration, each naming the file, the table and the key. */
final class ConfigProblems {
  private final String file;
  private final List<String> messages = new ArrayList<>();

  ConfigProblems(final String file, final List<String> existing) {
    this.file = file;
    messages.addAll(existing);
  }

  /** Adds {@code file: table key: detail}; {@code table} or {@code key} may be empty. */
  void add(final String table, final String key, final String detail) {
    final String where = (table.isEmpty() ? "" : " " + table) + (key.isEmpty() ? "" : " " + key);
    messages.add(file + ":" + where + ": " + detail);
  }

  List<String> messages() {
    return List.copyOf(messages);
  }

  boolean isEmpty() {
    return messages.isEmpty();
  }
}
