package dev.ward.bench;

import java.util.Comparator;

/**
 * One finding of a rule in a file, the unit the accuracy mode counts: a file counts once per rule
 * however many findings it has.
 *
 * @param file the path of the file, relative to the scanned project
 * @param rule the rule id, such as {@code ward:java/sql-injection}
 */
record WardFinding(String file, String rule) implements Comparable<WardFinding> {

  private static final Comparator<WardFinding> ORDER =
      Comparator.comparing(WardFinding::rule).thenComparing(WardFinding::file);

  @Override
  public int compareTo(final WardFinding other) {
    return ORDER.compare(this, other);
  }
}
