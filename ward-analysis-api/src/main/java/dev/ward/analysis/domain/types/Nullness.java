package dev.ward.analysis.domain.types;

/** Whether a declaration or expression may be null, as its annotations and scopes say. */
public enum Nullness {
  /** Annotated as possibly null. */
  NULLABLE,
  /** Annotated, or in a scope that makes it, never null. */
  NON_NULL,
  /** Nothing says; a rule must not report a finding that depends on it. */
  UNKNOWN
}
