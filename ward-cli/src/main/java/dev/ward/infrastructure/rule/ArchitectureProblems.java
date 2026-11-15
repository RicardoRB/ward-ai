package dev.ward.infrastructure.rule;

import java.util.ArrayList;
import java.util.List;

/** The problems found in one {@code ward-architecture.toml}, each naming the file, rule and key. */
final class ArchitectureProblems {
  private final String file;
  private final List<String> problems = new ArrayList<>();

  ArchitectureProblems(final String file) {
    this.file = file;
  }

  /** Records a problem; {@code rule} and {@code key} may be empty. */
  void add(final String rule, final String key, final String detail) {
    final String where =
        (rule.isEmpty() ? "" : " rule '" + rule + "'") + (key.isEmpty() ? "" : " " + key);
    problems.add(file + ":" + where + ": " + detail);
  }

  /** Records a problem of the whole file. */
  void add(final String detail) {
    problems.add(file + ": " + detail);
  }

  int count() {
    return problems.size();
  }

  List<String> all() {
    return List.copyOf(problems);
  }
}
