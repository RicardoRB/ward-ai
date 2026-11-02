package dev.ward.domain.init;

/** Classification produced while resolving a managed tool version. */
public enum CompatibilityOutcome {
  COMPATIBLE,
  UPGRADE_RECOMMENDED,
  INCOMPATIBLE,
  UNKNOWN,
  UNRESOLVABLE,
  /** No catalog version runs on the project's runtime JDK and build tool; the gate is omitted. */
  UNAVAILABLE_ON_JDK
}
