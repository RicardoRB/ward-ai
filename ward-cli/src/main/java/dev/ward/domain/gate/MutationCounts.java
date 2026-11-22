package dev.ward.domain.gate;

import java.util.List;

/**
 * The mutants of one mutation report, or why the report cannot be used.
 *
 * @param detected the killed or timed-out mutants
 * @param undetected the survived mutants and those no test covers
 * @param survivors the surviving mutants as the tool names them, when its report lists them
 * @param problem why the report cannot be used, or {@code null} when the counts are usable
 * @param survivors where the undetected mutants are, when the tool says so
 */
public record MutationCounts(int detected, int undetected, List<String> survivors, String problem) {
  private static final int PERCENT = 100;

  /** Copies the survivors. */
  public MutationCounts {
    survivors = List.copyOf(survivors);
  }

  /** Returns the counts of a usable report. */
  public static MutationCounts of(final int detected, final int undetected) {
    return of(detected, undetected, List.of());
  }

  /** Returns the counts of a usable report that names its surviving mutants. */
  public static MutationCounts of(
      final int detected,
      final int undetected,
      final List<String> survivors
  ) {
    return new MutationCounts(detected, undetected, survivors, null);
  }

  /** Returns a report that cannot be used, for {@code problem}. */
  public static MutationCounts unusable(final String problem) {
    return new MutationCounts(0, 0, List.of(), problem);
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
    return (long) detected * PERCENT < (long)minimum * valid();
  }
}
