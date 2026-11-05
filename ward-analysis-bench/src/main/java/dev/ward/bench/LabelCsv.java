package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads a corpus's {@code labels.csv}: a header line, then {@code file,rule,vulnerable} rows. */
final class LabelCsv {

  private static final String HEADER = "file,rule,vulnerable";
  private static final String RULE_PREFIX = "ward:";
  private static final String TRUE = "true";
  private static final String FALSE = "false";
  private static final int COLUMNS = 3;
  private static final int FILE = 0;
  private static final int RULE = 1;
  private static final int VULNERABLE = 2;

  private LabelCsv() {}

  /**
   * Reads the labels of one corpus.
   *
   * @param labels the labels file
   * @return the labels, in file order
   * @throws IllegalArgumentException if a row is malformed
   */
  static List<AccuracyLabel> read(final Path labels) {
    try {
      return parse(Files.readAllLines(labels, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the labels file " + labels, e);
    }
  }

  static List<AccuracyLabel> parse(final List<String> lines) {
    final List<String> rows =
        lines.stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
    if (rows.isEmpty() || !HEADER.equals(rows.get(0))) {
      throw new IllegalArgumentException("Labels must start with the header " + HEADER);
    }
    final List<AccuracyLabel> labels = new ArrayList<>();
    for (final String row : rows.subList(1, rows.size())) {
      labels.add(label(row));
    }
    return List.copyOf(labels);
  }

  private static AccuracyLabel label(final String row) {
    final String[] columns = row.split(",", -1);
    if (columns.length != COLUMNS) {
      throw new IllegalArgumentException("Label row must have three columns: " + row);
    }
    final String rule = columns[RULE];
    if (!rule.startsWith(RULE_PREFIX)) {
      throw new IllegalArgumentException("Label rule must be a ward rule id: " + rule);
    }
    return new AccuracyLabel(columns[FILE], rule, vulnerable(columns[VULNERABLE]));
  }

  private static boolean vulnerable(final String value) {
    if (TRUE.equals(value)) {
      return true;
    }
    if (FALSE.equals(value)) {
      return false;
    }
    throw new IllegalArgumentException("Label vulnerable must be true or false: " + value);
  }
}
