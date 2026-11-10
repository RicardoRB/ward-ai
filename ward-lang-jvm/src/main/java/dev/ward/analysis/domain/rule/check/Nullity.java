package dev.ward.analysis.domain.rule.check;

/**
 * What a null data-flow knows about a local variable at a point; a variable it lacks is unknown.
 */
enum Nullity {
  /** The variable holds null on every path that reaches the point. */
  NULL,
  /** The variable holds a value that is not null on every path, from a literal or a check. */
  NONNULL,
  /** The variable holds a value that is not null because the code already dereferenced it. */
  DEREFED,
  /**
   * The variable is a parameter that its annotation says may be null, and nothing has checked it.
   */
  PARAMETER;

  /** Tells whether the value is known not to be null. */
  boolean isNonNull() {
    return this == NONNULL || this == DEREFED;
  }
}
