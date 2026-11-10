package dev.ward.analysis.domain.scope;

/** Whether code nested in a class body sees the names the class declares. */
public enum ClassScopes {
  /** Nested scopes see the members, as in Java, Kotlin, Swift and JavaScript. */
  VISIBLE,
  /** Only the class body itself sees them, so a method skips them, as in Python. */
  SKIPPED
}
