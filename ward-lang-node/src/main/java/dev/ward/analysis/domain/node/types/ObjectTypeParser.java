package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Reads object types: properties, methods, call signatures and index signatures. */
final class ObjectTypeParser {
  private static final String OPEN_PAREN = "(";
  private static final String LESS = "<";
  private static final String COLON = ":";
  private static final String CLOSE_BRACKET = "]";
  private static final String NEW = "new";

  private final TokenCursor cursor;
  private final Supplier<TsType> type;
  private final SigParser signatures;

  ObjectTypeParser(
      final TokenCursor cursor, final Supplier<TsType> type, final SigParser signatures) {
    this.cursor = cursor;
    this.type = type;
    this.signatures = signatures;
  }

  /** Reads {@code { a: string; m(): void }} at the current position. */
  TsType objectType() {
    cursor.skip(1);
    final List<Member> members = new ArrayList<>();
    while (!cursor.hasFailed() && !cursor.at("}")) {
      members.add(member());
      if (!cursor.accept(";")) {
        cursor.accept(",");
      }
    }
    cursor.expect("}");
    return cursor.hasFailed() ? TsType.UNKNOWN : new TsType.Obj(members);
  }

  private Member member() {
    final int flags = readonlyFlag();
    if (cursor.at(OPEN_PAREN) || cursor.at(LESS)) {
      return callable("()", flags);
    }
    if (cursor.at("[")) {
      return indexMember(flags);
    }
    final String name = unquote(cursor.peek(0));
    if (name.isEmpty() || isConstructSignature(name)) {
      cursor.fail();
      return new Member("?", TsType.UNKNOWN, List.of(), 0, List.of());
    }
    cursor.skip(1);
    final int all = flags | (cursor.accept("?") ? Member.OPTIONAL : 0);
    if (cursor.at(OPEN_PAREN) || cursor.at(LESS)) {
      return callable(name, all);
    }
    cursor.expect(COLON);
    return new Member(name, type.get(), List.of(), all, List.of());
  }

  private int readonlyFlag() {
    if (cursor.at("readonly")
        && !TokenCursor.isPunctuation(cursor.peek(1))
        && !COLON.equals(cursor.peek(1))) {
      cursor.skip(1);
      return Member.READONLY;
    }
    return 0;
  }

  private boolean isConstructSignature(final String name) {
    return NEW.equals(name) && (OPEN_PAREN.equals(cursor.peek(1)) || LESS.equals(cursor.peek(1)));
  }

  private Member callable(final String name, final int flags) {
    final Sig sig = signatures.signature(COLON);
    return new Member(name, new TsType.Fn(sig), List.of(sig), flags, List.of());
  }

  private Member indexMember(final int flags) {
    cursor.skip(1);
    while (!cursor.hasFailed() && !cursor.at(CLOSE_BRACKET)) {
      cursor.skip(1);
      if (!cursor.isOpen()) {
        cursor.fail();
      }
    }
    cursor.expect(CLOSE_BRACKET);
    cursor.expect(COLON);
    return new Member("[]", type.get(), List.of(), flags, List.of());
  }

  private static String unquote(final String token) {
    final boolean quoted =
        token.length() >= 2 && (token.charAt(0) == '"' || token.charAt(0) == '\'');
    return quoted ? token.substring(1, token.length() - 1) : token;
  }
}
