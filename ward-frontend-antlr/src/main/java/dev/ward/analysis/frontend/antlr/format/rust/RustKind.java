package dev.ward.analysis.frontend.antlr.format.rust;

/** How a token spaces against its neighbours. */
enum RustKind {
  /** A name, literal or other token that can end an operand. */
  OPERAND,
  /** A keyword: it is followed by a space. */
  KEYWORD,
  /** A lifetime or loop label. */
  LIFETIME,
  /** A binary operator, or any operator with a space on each side. */
  BINARY,
  /** A prefix operator: it attaches to its operand. */
  PREFIX,
  /** {@code ?} after an operand. */
  POSTFIX,
  /** {@code ||} that opens a closure without parameters. */
  EMPTY_CLOSURE,
  /** {@code !} of a macro invocation. */
  BANG,
  /** {@code (} or {@code [}. */
  OPEN,
  /** {@code )} or {@code ]}. */
  CLOSE,
  /** {@code {}. */
  LEFT_BRACE,
  /** {@code }}. */
  RIGHT_BRACE,
  /** {@code ,}. */
  COMMA,
  /** {@code ;}. */
  SEMI,
  /** {@code :}. */
  COLON,
  /** {@code .}. */
  DOT,
  /** {@code ::}. */
  PATH_SEPARATOR,
  /** {@code ..}, {@code ..=} or {@code ...}. */
  RANGE,
  /** {@code #}. */
  POUND,
  /** {@code $}. */
  DOLLAR,
  /** A {@code <} of generic parameters or arguments. */
  ANGLE_OPEN,
  /** A {@code <} of a qualified path. */
  QUALIFIED_OPEN,
  /** A {@code >} that closes generic brackets. */
  ANGLE_CLOSE,
  /** The first {@code |} of closure parameters. */
  PIPE_OPEN,
  /** The second {@code |} of closure parameters. */
  PIPE_CLOSE,
  /** The {@code |} between the alternatives of a pattern. */
  PATTERN_BAR,
  /** A comment. */
  COMMENT
}
