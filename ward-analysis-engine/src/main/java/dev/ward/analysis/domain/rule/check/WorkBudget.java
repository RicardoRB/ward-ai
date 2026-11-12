package dev.ward.analysis.domain.rule.check;

/**
 * The matching work one rule may spend on one file, counted in characters read by the regex. Java
 * regex backtracks, so a pattern that goes wrong reads a text many times over; going over the
 * budget stops the match instead of hanging.
 */
final class WorkBudget {

  private static final long FLOOR = 1_000_000L;
  private static final long PER_CHARACTER = 200L;

  private long remaining;

  /** Creates the budget for a text of {@code length} characters. */
  WorkBudget(final int length) {
    this.remaining = Math.max(FLOOR, PER_CHARACTER * length);
  }

  /** Spends one character of work. */
  void spend() {
    if (--remaining < 0) {
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
