package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Reads tuple types and type argument lists. */
final class TupleParser {
  private static final String COMMA = ",";
  private static final String COLON = ":";
  private static final String QUESTION = "?";
  private static final String CLOSE_BRACKET = "]";
  private static final String GREATER = ">";

  private final TokenCursor cursor;
  private final Supplier<TsType> type;

  TupleParser(final TokenCursor cursor, final Supplier<TsType> type) {
    this.cursor = cursor;
    this.type = type;
  }

  /** Reads {@code [a: string, b?: number]} at the current position. */
  TsType tuple() {
    cursor.skip(1);
    final List<TsType> elements = new ArrayList<>();
    while (!cursor.hasFailed() && !cursor.at(CLOSE_BRACKET)) {
      cursor.accept("...");
      skipLabel();
      elements.add(type.get());
      cursor.accept(QUESTION);
      if (!cursor.at(CLOSE_BRACKET)) {
        cursor.expect(COMMA);
      }
    }
    cursor.expect(CLOSE_BRACKET);
    return cursor.hasFailed() ? TsType.UNKNOWN : new TsType.Tuple(elements);
  }

  /** Skips the label of a named tuple element: {@code name:} or {@code name?:}. */
  private void skipLabel() {
    final boolean optional = QUESTION.equals(cursor.peek(1));
    final int colon = optional ? 2 : 1;
    if (!TokenCursor.isPunctuation(cursor.peek(0)) && COLON.equals(cursor.peek(colon))) {
      cursor.skip(colon + 1);
    }
  }

  /** Reads {@code <A, B>} when present. */
  List<TsType> typeArguments() {
    final List<TsType> args = new ArrayList<>();
    if (cursor.accept("<")) {
      while (!cursor.hasFailed() && !cursor.at(GREATER)) {
        args.add(type.get());
        if (!cursor.at(GREATER)) {
          cursor.expect(COMMA);
        }
      }
      cursor.expect(GREATER);
    }
    return args;
  }
}
