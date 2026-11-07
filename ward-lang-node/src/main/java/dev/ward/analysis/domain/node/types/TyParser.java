package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a type from the tokens of its source text. What the parser does not model, such as mapped
 * and conditional types, makes the whole type {@link TsType#UNKNOWN}.
 */
public final class TyParser {
  private static final String OPEN_PAREN = "(";
  private static final String OPEN_BRACKET = "[";
  private static final String CLOSE_BRACKET = "]";
  private static final String OPEN_BRACE = "{";
  private static final String LESS = "<";
  private static final String DOT = ".";
  private static final String NEW = "new";
  private static final String UNION = "|";
  private static final String INTERSECTION = "&";
  private static final Set<String> MODIFIERS = Set.of("readonly", "public", "private", "protected");
  private static final Set<String> KEYWORDS =
      Set.of("keyof", "typeof", "infer", "unique", "import", "extends");

  private final TokenCursor cursor;
  private final SigParser signatures;
  private final ObjectTypeParser objects;
  private final TupleParser tuples;

  private TyParser(final List<String> tokens) {
    this.cursor = new TokenCursor(tokens);
    this.signatures = new SigParser(cursor, this::type);
    this.objects = new ObjectTypeParser(cursor, this::type, signatures);
    this.tuples = new TupleParser(cursor, this::type);
  }

  /** Returns the type written as {@code tokens}, or {@link TsType#UNKNOWN}. */
  public static TsType parse(final List<String> tokens) {
    final TyParser parser = new TyParser(tokens);
    final TsType type = parser.type();
    return parser.cursor.hasFailed() || !parser.cursor.isFinished() ? TsType.UNKNOWN : type;
  }

  private TsType type() {
    if (cursor.hasFailed()) {
      return TsType.UNKNOWN;
    }
    final TsType first = union();
    return cursor.at("extends") ? cursor.fail() : first;
  }

  private TsType union() {
    cursor.accept(UNION);
    final List<TsType> parts = new ArrayList<>();
    parts.add(intersection());
    while (!cursor.hasFailed() && cursor.accept(UNION)) {
      parts.add(intersection());
    }
    return parts.size() == 1 ? parts.get(0) : new TsType.Union(parts);
  }

  private TsType intersection() {
    cursor.accept(INTERSECTION);
    final List<TsType> parts = new ArrayList<>();
    parts.add(postfix());
    while (!cursor.hasFailed() && cursor.accept(INTERSECTION)) {
      parts.add(postfix());
    }
    return parts.size() == 1 ? parts.get(0) : new TsType.Inter(parts);
  }

  private TsType postfix() {
    TsType current = primary();
    while (!cursor.hasFailed() && cursor.at(OPEN_BRACKET)) {
      if (!CLOSE_BRACKET.equals(cursor.peek(1))) {
        return cursor.fail();
      }
      cursor.skip(2);
      current = TsType.arrayOf(current);
    }
    return current;
  }

  private TsType primary() {
    final String token = cursor.peek(0);
    if (token.isEmpty() || KEYWORDS.contains(token)) {
      return cursor.fail();
    }
    cursor.skipModifiers(MODIFIERS);
    return switch (cursor.peek(0)) {
      case OPEN_PAREN -> parenthesized();
      case LESS -> function();
      case OPEN_BRACE -> objects.objectType();
      case OPEN_BRACKET -> tuples.tuple();
      default -> named();
    };
  }

  private TsType parenthesized() {
    if (cursor.isFunctionAhead()) {
      return function();
    }
    cursor.skip(1);
    final TsType inner = type();
    cursor.expect(")");
    return inner;
  }

  private TsType function() {
    final Sig sig = signatures.signature("=>");
    return cursor.hasFailed() ? TsType.UNKNOWN : new TsType.Fn(sig);
  }

  private TsType named() {
    final String first = cursor.peek(0);
    if (NEW.equals(first) || TokenCursor.isPunctuation(first) || first.startsWith("`")) {
      return cursor.fail();
    }
    cursor.skip(1);
    final StringBuilder name = new StringBuilder(first);
    while (cursor.at(DOT) && !cursor.peek(1).isEmpty()) {
      name.append(DOT).append(cursor.peek(1));
      cursor.skip(2);
    }
    final List<TsType> args = tuples.typeArguments();
    return cursor.hasFailed() ? TsType.UNKNOWN : new TsType.Named(name.toString(), args, null);
  }
}
