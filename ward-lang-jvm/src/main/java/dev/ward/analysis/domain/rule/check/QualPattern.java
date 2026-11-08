package dev.ward.analysis.domain.rule.check;

/** The type qualifier patterns the analysis reports. */
enum QualPattern {
  /** A value that always has the qualifier reaches a place that requires it never. */
  ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED,
  /** A value that never has the qualifier reaches a place that requires it always. */
  NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED,
  /** An unknown value reaches a place that strictly requires the qualifier. */
  UNKNOWN_VALUE_USED_WHERE_ALWAYS_STRICTLY_REQUIRED,
  /** Two values with conflicting qualifiers are compared. */
  COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS,
  /** A value that may have the qualifier reaches a place that requires it always. */
  MAYBE_SOURCE_VALUE_REACHES_ALWAYS_SINK,
  /** A value that may have the qualifier reaches a place that requires it never. */
  MAYBE_SOURCE_VALUE_REACHES_NEVER_SINK,
  /** A parameter explicitly marked unknown reaches a place that requires it always. */
  EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_ALWAYS_SINK,
  /** A parameter explicitly marked unknown reaches a place that requires it never. */
  EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK
}
