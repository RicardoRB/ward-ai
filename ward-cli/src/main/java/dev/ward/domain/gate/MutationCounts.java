package dev.ward.domain.gate;

/**
 * The mutants of one mutation report, or why the report cannot be used.
 *
 * @param detected the killed or timed-out mutants
 * @param undetected the survived mutants and those no test covers
 * @param problem why the report cannot be used, or {@code null} when the counts are usable
 */
public record MutationCounts(int detected, int undetected, String problem) {
  private static final int PERCENT = 100;

  /** Returns the counts of a usable report. */
  public static MutationCounts of(final int detected, final int undetected) {
    return new MutationCounts(detected, undetected, null);
  }

  /** Returns a report that cannot be used, for {@code problem}. */
  public static MutationCounts unusable(final String problem) {
    return new MutationCounts(0, 0, problem);
  }

  /** Returns whether the report could be read. */
  public boolean usable() {
    return problem == null;
  }

  /** Returns the mutants that count toward the score: detected and undetected ones. */
  public int valid() {
    return detected + undetected;
  }

  /** Returns the mutation score, rounded down to a whole percentage, for display. */
  public int score() {
    return valid() == 0 ? 0 : detected * PERCENT / valid();
  }

  /** Returns whether the exact score, detected / valid × 100, is below {@code minimum}. */
  public boolean below(final int minimum) {
    return (long) detected * PERCENT < (long) minimum * valid();
  }
}
