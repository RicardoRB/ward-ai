package dev.ward.application.gate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The pairs of rules that contradict each other: no code can pass both. The list is the resource
 * {@code rule-conflicts.txt}, one {@code <rule> | <rule> | <why>} row per pair.
 */
final class RuleConflicts {
  private static final String RESOURCE = "rule-conflicts.txt";
  private static final String SEPARATOR = " \\| ";
  private static final int FIELDS = 3;

  /** One pair of contradicting rule ids and the reason. */
  record Pair(String first, String second, String reason) {}

  private RuleConflicts() {}

  /** Returns the pairs of the resource. */
  static List<Pair> pairs() {
    try (InputStream stream = RuleConflicts.class.getResourceAsStream(RESOURCE)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.startsWith("#") && !line.isBlank())
          .map(line -> line.split(SEPARATOR, FIELDS))
          .map(fields -> new Pair(fields[0], fields[1], fields[2]))
          .toList();
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
