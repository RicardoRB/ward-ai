package dev.ward.domain.gate;

/**
 * The production lines of one coverage report, or why the report cannot be used.
 *
 * @param covered the executable lines that tests ran
 * @param executable the executable lines
 * @param problem why the report cannot be used, or {@code null} when the counts are usable
 */
public record LineCoverage(long covered, long executable, String problem) {
  private static final long PERCENT = 100;

  /** Returns the counts of a usable report. */
  public static LineCoverage of(final long covered, final long executable) {
    return new LineCoverage(covered, executable, null);
  }

  /** Returns a report that cannot be used, for {@code problem}. */
  public static LineCoverage unusable(final String problem) {
    return new LineCoverage(0, 0, problem);
  }

  /** Returns whether the report could be read. */
  public boolean usable() {
    return problem == null;
  }

  /** Returns the line coverage, rounded down to a whole percentage, for display. */
  public long percent() {
    return executable == 0 ? 0 : covered * PERCENT / executable;
  }

  /** Returns whether the exact coverage, covered / executable × 100, is below {@code minimum}. */
  public boolean below(final int minimum) {
    return covered * PERCENT < minimum * executable;
  }
}
