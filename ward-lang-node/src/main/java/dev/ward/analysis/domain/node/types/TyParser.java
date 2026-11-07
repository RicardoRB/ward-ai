package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a type from the tokens of its source text. What the parser does not model, such as mapped
 * and conditional types, makes the whole type {@link Ty#UNKNOWN}.
 */
public final class TyParser {
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String OPEN_BRACKET = "[";
  private static final String CLOSE_BRACKET = "]";
  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";
  private static final String COMMA = ",";
  private static final String COLON = ":";
  private static final String QUESTION = "?";
  private static final String ARROW = "=>";
  private static final String LESS = "<";
  private static final String GREATER = ">";
  private static final String DOT = ".";
  private static final String SEPARATOR_SEMICOLON = ";";
  private static final String NEW = "new";
  private static final Set<String> MODIFIERS = Set.of("readonly", "public", "private", "protected");
  private static final Set<String> KEYWORDS =
      Set.of("keyof", "typeof", "infer", "unique", "import", "extends");

  private final List<String> tokens;
  private int position;
  private boolean failed;

  private TyParser(final List<String> tokens) {
    this.tokens = tokens;
  }

  /** Returns the type written as {@code tokens}, or {@link Ty#UNKNOWN}. */
  public static Ty parse(final List<String> tokens) {
    final TyParser parser = new TyParser(tokens);
    final Ty type = parser.type();
    return parser.failed || parser.position != tokens.size() ? Ty.UNKNOWN : type;
  }

  private String peek(final int ahead) {
    return position + ahead < tokens.size() ? tokens.get(position + ahead) : "";
  }

  private boolean at(final String token) {
    return peek(0).equals(token);
  }

  private boolean accept(final String token) {
    if (at(token)) {
      position++;
      return true;
    }
    return false;
  }

  private void expect(final String token) {
    if (!accept(token)) {
      failed = true;
    }
  }

  private Ty fail() {
    failed = true;
    return Ty.UNKNOWN;
  }

  private Ty type() {
    if (failed) {
      return Ty.UNKNOWN;
    }
    final Ty first = union();
    if (at("extends")) {
      return fail();
    }
    return first;
  }

  private Ty union() {
    accept("|");
    final List<Ty> parts = new ArrayList<>();
    parts.add(intersection());
    while (!failed && accept("|")) {
      parts.add(intersection());
    }
    return parts.size() == 1 ? parts.get(0) : new Ty.Union(parts);
  }

  private Ty intersection() {
    accept("&");
    final List<Ty> parts = new ArrayList<>();
    parts.add(postfix());
    while (!failed && accept("&")) {
      parts.add(postfix());
    }
    return parts.size() == 1 ? parts.get(0) : new Ty.Inter(parts);
  }

  private Ty postfix() {
    Ty current = primary();
    while (!failed && at(OPEN_BRACKET)) {
      if (!CLOSE_BRACKET.equals(peek(1))) {
        return fail();
      }
      position += 2;
      current = new Ty.Arr(current);
    }
    return current;
  }

  private Ty primary() {
    final String token = peek(0);
    if (token.isEmpty() || KEYWORDS.contains(token)) {
      return fail();
    }
    while (MODIFIERS.contains(peek(0)) && !peek(1).isEmpty() && !isPunctuation(peek(1))) {
      position++;
    }
    return switch (peek(0)) {
      case OPEN_PAREN -> parenthesized();
      case LESS -> function();
      case OPEN_BRACE -> objectType();
      case OPEN_BRACKET -> tuple();
      default -> named();
    };
  }

  private static boolean isPunctuation(final String token) {
    return token.length() == 1
        && !Character.isLetterOrDigit(token.charAt(0))
        && token.charAt(0) != '_'
        && token.charAt(0) != '$';
  }

  private Ty parenthesized() {
    if (isFunctionAhead()) {
      return function();
    }
    position++;
    final Ty inner = type();
    expect(CLOSE_PAREN);
    return inner;
  }

  private boolean isFunctionAhead() {
    int depth = 0;
    for (int index = position; index < tokens.size(); index++) {
      final String token = tokens.get(index);
      if (OPEN_PAREN.equals(token)) {
        depth++;
      } else if (CLOSE_PAREN.equals(token)) {
        depth--;
        if (depth == 0) {
          return index + 1 < tokens.size() && ARROW.equals(tokens.get(index + 1));
        }
      }
    }
    return false;
  }

  private Ty function() {
    final Sig sig = signature(ARROW);
    return failed ? Ty.UNKNOWN : new Ty.Fn(sig);
  }

  /** Reads type parameters, parameters and the return type after {@code arrow}. */
  private Sig signature(final String arrow) {
    final List<String> typeParams = typeParams();
    expect(OPEN_PAREN);
    final List<Param> params = new ArrayList<>();
    while (!failed && !at(CLOSE_PAREN)) {
      param().ifPresent(params::add);
      if (!at(CLOSE_PAREN)) {
        expect(COMMA);
      }
    }
    expect(CLOSE_PAREN);
    expect(arrow);
    return new Sig(typeParams, params, returnType());
  }

  private Ty returnType() {
    if (peek(1).equals("is") && !isPunctuation(peek(0))) {
      position += 2;
      type();
      return Ty.named("boolean");
    }
    if (at("asserts") && !isPunctuation(peek(1))) {
      position++;
      if (peek(1).equals("is")) {
        position += 2;
        type();
      } else {
        position++;
      }
      return Ty.named("void");
    }
    return type();
  }

  private List<String> typeParams() {
    final List<String> names = new ArrayList<>();
    if (accept(LESS)) {
      int depth = 1;
      boolean expectName = true;
      while (!failed && depth > 0 && position < tokens.size()) {
        final String token = tokens.get(position++);
        if (LESS.equals(token)) {
          depth++;
        } else if (GREATER.equals(token)) {
          depth--;
        } else if (expectName && depth == 1 && !isPunctuation(token) && !"const".equals(token)) {
          names.add(token);
          expectName = false;
        } else if (COMMA.equals(token) && depth == 1) {
          expectName = true;
        }
      }
      failed |= depth > 0;
    }
    return names;
  }

  private java.util.Optional<Param> param() {
    final boolean rest = accept("...");
    final String name = peek(0);
    if (isPunctuation(name) || name.isEmpty()) {
      failed = true;
      return java.util.Optional.empty();
    }
    position++;
    final boolean optional = accept(QUESTION);
    final Ty type = accept(COLON) ? type() : Ty.UNKNOWN;
    if ("this".equals(name)) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(new Param(name, type, optional, rest));
  }

  private Ty tuple() {
    position++;
    final List<Ty> elements = new ArrayList<>();
    while (!failed && !at(CLOSE_BRACKET)) {
      accept("...");
      if (!isPunctuation(peek(0))
          && (COLON.equals(peek(1)) || QUESTION.equals(peek(1)) && COLON.equals(peek(2)))) {
        position += COLON.equals(peek(1)) ? 2 : 3;
      }
      elements.add(type());
      accept(QUESTION);
      if (!at(CLOSE_BRACKET)) {
        expect(COMMA);
      }
    }
    expect(CLOSE_BRACKET);
    return failed ? Ty.UNKNOWN : new Ty.Tuple(elements);
  }

  private Ty objectType() {
    position++;
    final List<Member> members = new ArrayList<>();
    while (!failed && !at(CLOSE_BRACE)) {
      members.add(member());
      if (!accept(SEPARATOR_SEMICOLON)) {
        accept(COMMA);
      }
    }
    expect(CLOSE_BRACE);
    return failed ? Ty.UNKNOWN : new Ty.Obj(members);
  }

  private Member member() {
    int flags = 0;
    if (at("readonly") && !isPunctuation(peek(1)) && !COLON.equals(peek(1))) {
      position++;
      flags |= Member.READONLY;
    }
    if (at(OPEN_PAREN) || at(LESS)) {
      final Sig sig = signature(COLON);
      return new Member("()", new Ty.Fn(sig), List.of(sig), flags, List.of());
    }
    if (at(OPEN_BRACKET)) {
      return indexMember(flags);
    }
    final String name = unquote(peek(0));
    if (name.isEmpty()
        || NEW.equals(name) && at(NEW) && (OPEN_PAREN.equals(peek(1)) || LESS.equals(peek(1)))) {
      failed = true;
      return new Member("?", Ty.UNKNOWN, List.of(), 0, List.of());
    }
    position++;
    if (accept(QUESTION)) {
      flags |= Member.OPTIONAL;
    }
    if (at(OPEN_PAREN) || at(LESS)) {
      final Sig sig = signature(COLON);
      return new Member(name, new Ty.Fn(sig), List.of(sig), flags, List.of());
    }
    expect(COLON);
    return new Member(name, type(), List.of(), flags, List.of());
  }

  private Member indexMember(final int flags) {
    position++;
    while (!failed && !at(CLOSE_BRACKET)) {
      position++;
      failed |= position >= tokens.size();
    }
    expect(CLOSE_BRACKET);
    expect(COLON);
    return new Member("[]", type(), List.of(), flags, List.of());
  }

  private static String unquote(final String token) {
    final boolean quoted =
        token.length() >= 2 && (token.charAt(0) == '"' || token.charAt(0) == '\'');
    return quoted ? token.substring(1, token.length() - 1) : token;
  }

  private Ty named() {
    final String first = peek(0);
    if (NEW.equals(first) || isPunctuation(first) || first.startsWith("`")) {
      return fail();
    }
    position++;
    final StringBuilder name = new StringBuilder(first);
    while (at(DOT) && !peek(1).isEmpty()) {
      name.append(DOT).append(peek(1));
      position += 2;
    }
    final List<Ty> args = new ArrayList<>();
    if (accept(LESS)) {
      while (!failed && !at(GREATER)) {
        args.add(type());
        if (!at(GREATER)) {
          expect(COMMA);
        }
      }
      expect(GREATER);
    }
    return failed ? Ty.UNKNOWN : new Ty.Named(name.toString(), args, null);
  }
}
