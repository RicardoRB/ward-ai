package dev.ward.analysis.domain.rule.check;

/**
 * What the nullness analysis knows about a variable at one point. {@link #UNKNOWN} never produces a
 * finding.
 */
enum FlowNullValue {
  /** Null on every path. */
  NULL,
  /** Null on every path, because an annotation says so. */
  NEVER,
  /** Non-null on every path. */
  NON_NULL,
  /** Non-null because the variable was dereferenced, so a later null check is redundant. */
  DEREFERENCED,
  /** Annotated as possibly null and not checked since. */
  NULLABLE,
  /** Null on some paths and non-null on others. */
  MIXED,
  /** Nothing is known. */
  UNKNOWN;

  /** Returns what is known where a path with {@code this} meets a path with {@code other}. */
  FlowNullValue join(final FlowNullValue other) {
    if (this == other) {
      return this;
    }
    if (this == UNKNOWN || other == UNKNOWN) {
      return UNKNOWN;
    }
    return joinKnown(other);
  }

  private FlowNullValue joinKnown(final FlowNullValue other) {
    if (isNullish() && other.isNullish()) {
      return NULL;
    }
    if (isNonNull() && other.isNonNull()) {
      return NON_NULL;
    }
    if (this == NULLABLE || other == NULLABLE) {
      return NULLABLE;
    }
    return MIXED;
  }

  /** Tells whether the value is null on every path. */
  boolean isNullish() {
    return this == NULL || this == NEVER;
  }

  /** Tells whether the value is non-null on every path. */
  boolean isNonNull() {
    return this == NON_NULL || this == DEREFERENCED;
  }
}
