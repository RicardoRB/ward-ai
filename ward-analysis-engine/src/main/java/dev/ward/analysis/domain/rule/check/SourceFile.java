package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;
import java.util.Objects;

/**
 * The path and text of the file a check runs on. A check run without a file sees {@link #UNKNOWN}.
 *
 * @param path the project-root-relative path with {@code /} separators, or empty
 * @param text the source text, or empty
 */
record SourceFile(String path, String text) {

  /** The file of a check run over a bare syntax tree. */
  static final SourceFile UNKNOWN = new SourceFile("", "");

  SourceFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(text, "text");
  }

  /** Reads the path and text of {@code unit}; a missing unit is {@link #UNKNOWN}. */
  static SourceFile of(final SourceUnit unit) {
    return unit == null ? UNKNOWN : new SourceFile(unit.path(), unit.text());
  }

  /** Returns the last segment of the path. */
  String name() {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /** Returns the lines of the text, without line terminators. */
  List<String> lines() {
    return text.isEmpty() ? List.of() : List.of(text.split("\r\n|\n|\r", -1));
  }
}
