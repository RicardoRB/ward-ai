package dev.ward.analysis.domain.api;

/** Who can see a declaration, from the most visible to the least. */
public enum ApiVisibility {
  /** Visible everywhere. */
  PUBLIC(3),
  /** Visible to subclasses (and the package, in Java). */
  PROTECTED(2),
  /** Visible in the package or module only; not part of an API. */
  PACKAGE(1),
  /** Visible in the declaring type only; not part of an API. */
  PRIVATE(0);

  private final int rank;

  ApiVisibility(final int rank) {
    this.rank = rank;
  }

  /** Returns whether this is strictly less visible than {@code other}. */
  public boolean lessVisibleThan(final ApiVisibility other) {
    return rank < other.rank;
  }
}
