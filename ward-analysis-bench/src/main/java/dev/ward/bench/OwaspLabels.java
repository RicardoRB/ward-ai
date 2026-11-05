package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads OWASP Benchmark's {@code expectedresults} CSV: {@code test name, category, real
 * vulnerability, cwe}. Each category of the three injection classes maps to one Ward rule, and each
 * test name maps to {@code <root>/<test name>.java}. Other categories give no label.
 */
final class OwaspLabels {

  private static final String COMMENT = "#";
  private static final String SUFFIX = ".java";
  private static final int COLUMNS = 3;
  private static final int NAME = 0;
  private static final int CATEGORY = 1;
  private static final int VULNERABLE = 2;
  private static final String TRUE = "true";
  private static final String FALSE = "false";
  private static final Map<String, String> RULES =
      Map.of(
          "sqli", "ward:java/sql-injection",
          "cmdi", "ward:java/command-injection",
          "pathtraver", "ward:java/path-injection");

  private OwaspLabels() {}

  /**
   * Reads the labels of the three injection categories.
   *
   * @param expectedResults the CSV file in the checkout
   * @param root the directory of the test sources, relative to the checkout root
   * @return one label per test of a mapped category
   */
  static List<AccuracyLabel> read(final Path expectedResults, final String root) {
    try {
      return parse(Files.readAllLines(expectedResults, StandardCharsets.UTF_8), root);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + expectedResults, e);
    }
  }

  static List<AccuracyLabel> parse(final List<String> lines, final String root) {
    final List<AccuracyLabel> labels = new ArrayList<>();
    for (final String line : lines) {
      final String row = line.strip();
      if (!row.isEmpty() && !row.startsWith(COMMENT)) {
        label(row, root).ifPresent(labels::add);
      }
    }
    return List.copyOf(labels);
  }

  private static Optional<AccuracyLabel> label(final String row, final String root) {
    final String[] columns = row.split(",", -1);
    if (columns.length < COLUMNS) {
      throw new IllegalArgumentException("Expected results row has too few columns: " + row);
    }
    final String rule = RULES.get(columns[CATEGORY].strip());
    if (rule == null) {
      return Optional.empty();
    }
    final String file = root + "/" + columns[NAME].strip() + SUFFIX;
    return Optional.of(new AccuracyLabel(file, rule, vulnerable(columns[VULNERABLE].strip())));
  }

  private static boolean vulnerable(final String value) {
    if (TRUE.equals(value)) {
      return true;
    }
    if (FALSE.equals(value)) {
      return false;
    }
    throw new IllegalArgumentException("Real vulnerability must be true or false: " + value);
  }
}
