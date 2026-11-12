package dev.ward.analysis.domain.rule.check;

/**
 * The matching work one rule may spend on one region of a text, counted in characters read by the
 * regex. Java regex backtracks, so a pattern that goes wrong reads a text many times over; going
 * over the budget stops the match instead of hanging. A region that costs more than {@link
 * #PER_CHARACTER} reads per character is not a linear scan, and the first one ends the rule's run
 * on the file, so the time spent on a file stays bounded by one budget.
 */
final class WorkBudget {

  /** The least work a region may use, however short it is. */
  static final long FLOOR = 1_000_000L;

  /**
   * Reads per character of a region. The bundled rules read up to about 2,800 characters per
   * character of real text (a keyword-free word list through a lazy {@code [\w.-]{0,50}?} prefix),
   * so this leaves room above that and is still reached in well under a second by catastrophic
   * backtracking.
   */
  static final long PER_CHARACTER = 5_000L;

  private long remaining = FLOOR;

  /** Starts a region of {@code length} characters with a fresh allowance. */
  void reset(final int length) {
    remaining = Math.max(FLOOR, PER_CHARACTER * length);
  }

  /** Spends one character of work. */
  void spend() {
    remaining--;
    if (remaining < 0) {
      throw new BudgetExceededException();
    }
  }

  /** Thrown when the work of a match goes over its budget. */
  static final class BudgetExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    BudgetExceededException() {
      super("work budget exceeded", null, false, false);
    }
  }
}
