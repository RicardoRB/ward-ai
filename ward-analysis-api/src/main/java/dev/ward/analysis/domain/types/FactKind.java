package dev.ward.analysis.domain.types;

/** The questions a rule can ask about a span; a resolver answers only the kinds it is asked. */
public enum FactKind {
  /** The type of an expression. */
  TYPE,
  /** The shape and supertypes of a type declaration or of an expression's type. */
  SHAPE,
  /** The target of an invocation. */
  CALL,
  /** The declared annotations of a declaration, and those of its enclosing scopes. */
  ANNOTATIONS,
  /** The methods a method declaration overrides. */
  OVERRIDES,
  /** The checked exceptions a method declares or an invocation can throw. */
  THROWS,
  /** The compile-time constant value of an expression. */
  CONSTANT,
  /** The methods, fields, all supertypes and annotations of the type of an expression. */
  MEMBERS;
}
