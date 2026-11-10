package dev.ward.analysis.domain.scope;

/** What a binding declares. */
public enum BindingKind {
  /** A variable, field or property. */
  VARIABLE,
  /** A parameter of a function, lambda or catch clause. */
  PARAMETER,
  /** A function, method or constructor. */
  FUNCTION,
  /** A class, interface, enum, struct or protocol. */
  CLASS,
  /** A name an import brings in. */
  IMPORT,
  /** A type alias, generic parameter or type-only declaration. */
  TYPE,
  /** A constant: a Kotlin {@code val}, a Swift {@code let}, a Java {@code final} field. */
  CONSTANT,
  /** A name the language or its environment provides, such as a Python builtin. */
  BUILTIN
}
