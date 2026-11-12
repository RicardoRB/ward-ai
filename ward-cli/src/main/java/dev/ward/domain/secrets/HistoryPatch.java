package dev.ward.domain.secrets;

import java.util.List;
import java.util.Objects;

/**
 * The lines one commit added to one file.
 *
 * @param commit the short id of the commit
 * @param path the project-relative path of the file, with {@code /} separators
 * @param lines the added lines, in order
 */
public record HistoryPatch(String commit, String path, List<AddedLine> lines) {

  /**
   * One added line.
   *
   * @param number the line number in the file as the commit left it, 1-based
   * @param text the text of the line, without its terminator
   */
  public record AddedLine(int number, String text) {

    /** Validates the line. */
    public AddedLine {
      Objects.requireNonNull(text, "text");
      if (number < 1) {
        throw new IllegalArgumentException("line numbers are 1-based");
      }
    }
  }

  /** Validates the fields and copies the lines. */
  public HistoryPatch {
    Objects.requireNonNull(commit, "commit");
    Objects.requireNonNull(path, "path");
    lines = List.copyOf(lines);
  }
}
