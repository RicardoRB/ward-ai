package dev.ward.analysis.domain.node.lint;

/** The kinds of value that the restrict-* rules tell apart, from a known type. */
enum ValueKind {
  /** The {@code any} type. */
  ANY,
  /** The {@code unknown} type, or a type the rules do not classify. */
  OTHER,
  /** {@code boolean}, or a boolean literal. */
  BOOLEAN,
  /** {@code null}, {@code undefined} or {@code void}. */
  NULLISH,
  /** {@code number}, or a number literal. */
  NUMBER,
  /** {@code bigint}. */
  BIGINT,
  /** {@code string}, or a string literal. */
  STRING,
  /** {@code never}. */
  NEVER,
  /** {@code RegExp}. */
  REGEXP,
  /** An array or tuple. */
  ARRAY,
  /** An object, a class instance or a function. */
  OBJECT
}
