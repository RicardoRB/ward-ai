package dev.ward.analysis.domain.scope;

/** What opens a lexical scope. */
public enum ScopeKind {
  /** The outermost scope, which holds the names a language or its environment provides. */
  BUILTIN,
  /** A whole file: a Python module, a script or ES module, a Java or Kotlin file, a Swift file. */
  MODULE,
  /** A class, interface, enum, struct, protocol or extension body, which declares members. */
  CLASS,
  /** A function, method, constructor or accessor, whose parameters it declares. */
  FUNCTION,
  /** A braced block, a loop or {@code catch} header, or a {@code switch} body. */
  BLOCK,
  /** A Python comprehension or generator expression, which has a scope of its own. */
  COMPREHENSION,
  /** A lambda, arrow function or closure expression. */
  LAMBDA
}
