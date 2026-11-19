package dev.ward.analysis.frontend.antlr.format.csharp;

/** What a token does in the syntax tree, when that decides the space around it. */
enum CSharpRole {
  /** No special role: the token pair rules decide. */
  NONE,
  /** A binary or ternary operator, with a space on both sides. */
  BINARY,
  /** A prefix operator, with no space after it. */
  PREFIX,
  /** A postfix operator or null-forgiving sign, with no space before it. */
  POSTFIX,
  /** The closing parenthesis of a cast, with no space after it. */
  CAST_CLOSE,
  /** The opening angle bracket of type arguments or parameters. */
  GENERIC_OPEN,
  /** The closing angle bracket of type arguments or parameters. */
  GENERIC_CLOSE,
  /** The question mark of a nullable type. */
  NULLABLE,
  /** The question mark of a null-conditional access. */
  NULL_CONDITIONAL,
  /** A colon with a space on both sides, as in a base list or a constraint. */
  COLON_SPACED,
  /** A brace of an array initializer, which stays where it is written. */
  ARRAY_BRACE,
  /** The dots of a spread element or a slice pattern, which take a space before what follows. */
  SPREAD,
  /** The sign of an overloaded operator, which stands next to its parameter list. */
  OPERATOR_NAME
}
