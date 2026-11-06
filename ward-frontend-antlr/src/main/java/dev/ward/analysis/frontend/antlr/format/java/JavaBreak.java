package dev.ward.analysis.frontend.antlr.format.java;

/** The break a token leaves before the next token in the layout. */
enum JavaBreak {
  /** No break was decided. */
  UNSET,
  /** Nothing between the two tokens. */
  NONE,
  /** One space between the two tokens. */
  SPACE,
  /** A line that prints a space flat and breaks inside a broken group. */
  LINE,
  /** A line break that always happens. */
  HARD,
  /** A continuation line, indented by the continuation levels. */
  CONTINUE
}
