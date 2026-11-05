package dev.ward.bench;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the findings of {@code ward check --format json}, one (file, rule) pair per finding. A
 * check that did not run is an error, so an unavailable rule never reads as no findings.
 */
final class WardFindings {

  private static final String CHECKS = "checks";
  private static final String ERROR = "error";
  private static final String STATUS = "status";
  private static final String FINDINGS = "findings";
  private static final String FILE = "file";
  private static final String RULE = "rule";
  private static final String FINDINGS_TRUNCATED = "findingsTruncated";
  private static final String PASSED = "PASSED";
  private static final String FAILED = "FAILED";

  private WardFindings() {}

  /**
   * Returns the distinct (file, rule) pairs of the findings in the output.
   *
   * @param json the standard output of the check command
   * @return the pairs, sorted
   * @throws IllegalStateException if a check did not run or its findings were truncated
   * @throws IllegalArgumentException if the output is not the check's JSON
   */
  static Set<WardFinding> read(final String json) {
    final Map<?, ?> document = object(MiniJson.parse(json));
    if (!document.containsKey(CHECKS)) {
      throw new IllegalStateException("The ward run produced no checks: " + document.get(ERROR));
    }
    final Set<WardFinding> found = new TreeSet<>();
    for (final Object check : list(document.get(CHECKS))) {
      readCheck(object(check), found);
    }
    return found;
  }

  private static void readCheck(final Map<?, ?> check, final Set<WardFinding> found) {
    final Object status = check.get(STATUS);
    if (!PASSED.equals(status) && !FAILED.equals(status)) {
      throw new IllegalStateException("The ward check did not run: status " + status);
    }
    if (Boolean.TRUE.equals(check.get(FINDINGS_TRUNCATED))) {
      throw new IllegalStateException("The ward check truncated its findings");
    }
    for (final Object finding : list(check.get(FINDINGS))) {
      final Map<?, ?> row = object(finding);
      found.add(new WardFinding(text(row.get(FILE)), text(row.get(RULE))));
    }
  }

  private static Map<?, ?> object(final Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new IllegalArgumentException("Expected a JSON object in the ward output");
  }

  private static List<?> list(final Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> items) {
      return items;
    }
    throw new IllegalArgumentException("Expected a JSON array in the ward output");
  }

  private static String text(final Object value) {
    if (value instanceof String string) {
      return string;
    }
    throw new IllegalArgumentException("Expected a string in the ward output");
  }
}
