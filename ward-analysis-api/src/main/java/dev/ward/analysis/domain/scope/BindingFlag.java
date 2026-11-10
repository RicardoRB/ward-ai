package dev.ward.analysis.domain.scope;

/** A property of a binding that scoping rules or rules over scopes depend on. */
public enum BindingFlag {
  /** In a function: the name stands for a module-level name ({@code global x}). */
  GLOBAL,
  /** In a function: the name stands for a name of an enclosing function ({@code nonlocal x}). */
  NONLOCAL,
  /** Declared with {@code const}. */
  CONST,
  /** Block scoped and in its temporal dead zone before the declaration ({@code let}, class). */
  LEXICAL,
  /** Hoisted to the top of its scope ({@code var} and function declarations in JavaScript). */
  HOISTED,
  /** Exists only in the type system, such as a TypeScript interface or type-only import. */
  TYPE_ONLY,
  /** Visible to other files: listed in {@code __all__} or exported. */
  EXPORTED,
  /** Bound by unpacking, a loop header or a pattern, rather than by a plain declaration. */
  DESTRUCTURED
}
