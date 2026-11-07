package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Reads type parameters, parameters and return types of function types and signatures. */
final class SigParser {
  private static final String COMMA = ",";
  private static final String CLOSE_PAREN = ")";
  private static final String LESS = "<";
  private static final String GREATER = ">";
  private static final String IS = "is";

  private final TokenCursor cursor;
  private final Supplier<TsType> type;

  SigParser(final TokenCursor cursor, final Supplier<TsType> type) {
    this.cursor = cursor;
    this.type = type;
  }

  /** Reads type parameters, parameters and the return type after {@code arrow}. */
  Sig signature(final String arrow) {
    final List<String> typeParams = typeParams();
    cursor.expect("(");
    final List<Param> params = new ArrayList<>();
    while (!cursor.hasFailed() && !cursor.at(CLOSE_PAREN)) {
      param().ifPresent(params::add);
      if (!cursor.at(CLOSE_PAREN)) {
        cursor.expect(COMMA);
      }
    }
    cursor.expect(CLOSE_PAREN);
    cursor.expect(arrow);
    return new Sig(typeParams, params, returnType());
  }

  private TsType returnType() {
    if (IS.equals(cursor.peek(1)) && !TokenCursor.isPunctuation(cursor.peek(0))) {
      cursor.skip(2);
      type.get();
      return TsType.named("boolean");
    }
    if (cursor.at("asserts") && !TokenCursor.isPunctuation(cursor.peek(1))) {
      cursor.skip(1);
      final boolean narrows = IS.equals(cursor.peek(1));
      cursor.skip(narrows ? 2 : 1);
      if (narrows) {
        type.get();
      }
      return TsType.named("void");
    }
    return type.get();
  }

  private List<String> typeParams() {
    final List<String> names = new ArrayList<>();
    if (!cursor.accept(LESS)) {
      return names;
    }
    int depth = 1;
    boolean expectName = true;
    while (!cursor.hasFailed() && depth > 0 && cursor.isOpen()) {
      final String token = cursor.take();
      depth += depthChange(token);
      expectName = nextExpectation(token, depth, expectName, names);
    }
    if (depth > 0) {
      cursor.fail();
    }
    return names;
  }

  /** Records a type parameter name when one is expected; returns whether another is expected. */
  private static boolean nextExpectation(
      final String token, final int depth, final boolean expecting, final List<String> names) {
    if (depth != 1) {
      return expecting;
    }
    if (COMMA.equals(token)) {
      return true;
    }
    if (expecting && isTypeParameterName(token)) {
      names.add(token);
      return false;
    }
    return expecting;
  }

  private static int depthChange(final String token) {
    if (LESS.equals(token)) {
      return 1;
    }
    return GREATER.equals(token) ? -1 : 0;
  }

  private static boolean isTypeParameterName(final String token) {
    return !TokenCursor.isPunctuation(token) && !"const".equals(token);
  }

  private Optional<Param> param() {
    final boolean rest = cursor.accept("...");
    final String name = cursor.peek(0);
    if (TokenCursor.isPunctuation(name) || name.isEmpty()) {
      cursor.fail();
      return Optional.empty();
    }
    cursor.skip(1);
    final boolean optional = cursor.accept("?");
    final TsType declared = cursor.accept(":") ? type.get() : TsType.UNKNOWN;
    return "this".equals(name)
        ? Optional.empty()
        : Optional.of(new Param(name, declared, optional, rest));
  }
}
