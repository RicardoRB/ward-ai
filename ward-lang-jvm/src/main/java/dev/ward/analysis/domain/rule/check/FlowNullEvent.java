package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;

/**
 * Something the nullness analysis found at one place.
 *
 * @param kind what was found
 * @param span where
 * @param name the variable, field or method the finding is about
 */
record FlowNullEvent(Kind kind, Span span, String name) {

  /** What the analysis can find. */
  enum Kind {
    /** A variable that is null on every path is dereferenced. */
    DEREF_NULL,
    /** The same, on a path that came through an exception edge. */
    DEREF_NULL_EXCEPTION,
    /** A variable that is null on some path is dereferenced. */
    DEREF_MIXED,
    /** The same, on a path that came through an exception edge. */
    DEREF_MIXED_EXCEPTION,
    /** A variable that is null on every path is closed. */
    CLOSE_NULL,
    /** A null value is tested with instanceof. */
    INSTANCEOF_NULL,
    /** A variable is compared with null after it was dereferenced. */
    CHECK_AFTER_DEREF,
    /** A null value is passed for a parameter that must not be null. */
    PARAM_NULL,
    /** A null value is returned from a method that must not return null. */
    RETURN_NULL,
    /** A null value is stored in a field that must not be null. */
    FIELD_NULL,
    /** Null is returned from a method that returns an Optional. */
    OPTIONAL_RETURN_NULL,
    /** A value that may be null reaches a sink that must not be null. */
    MAYBE_TO_ALWAYS,
    /** A value annotated as always null reaches a sink that must not be null. */
    NEVER_TO_ALWAYS,
    /** A value that is never null reaches a sink that must be null. */
    ALWAYS_TO_NEVER,
    /** A value that may be null reaches a sink that must be null. */
    MAYBE_TO_NEVER,
    /** Two values with incompatible qualifiers are compared. */
    COMPARE_INCOMPATIBLE
  }

  /** Returns the event at {@code span}; a loop calls this, not the constructor. */
  static FlowNullEvent at(final Kind kind, final Span span, final String name) {
    return new FlowNullEvent(kind, span, name);
  }
}
