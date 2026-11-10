package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code package.json} read for the keys that dead-code rules need, each with the line and column
 * where its key is written, so a finding can point into the manifest. The reader is a small JSON
 * parser: a file it cannot parse yields no manifest, so a rule that needs it reports nothing.
 */
public final class NodeManifest {

  private static final int HEX_DIGITS = 4;
  private static final int HEX_RADIX = 16;
  private static final String LITERAL_TRUE = "true";
  private static final String LITERAL_FALSE = "false";
  private static final String LITERAL_NULL = "null";

  private final Value.Obj root;

  private NodeManifest(final Value.Obj root) {
    this.root = root;
  }

  /** One member of an object: its key, where the key is written, and its value. */
  public record Member(String key, int line, int column, Value value) {

    /**
     * Validates the parts.
     *
     * @throws NullPointerException if the key or value is missing
     */
    public Member {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(value, "value");
    }
  }

  /** A JSON value, with the members of objects kept in the order the file writes them. */
  public sealed interface Value {

    /** A string value. */
    record Text(String value) implements Value {}

    /** An array value. */
    record Array(List<Value> items) implements Value {

      /** Copies the items in and out. */
      public Array {
        items = List.copyOf(items);
      }

      @Override
      public List<Value> items() {
        return List.copyOf(items);
      }
    }

    /** An object value. */
    record Obj(List<Member> members) implements Value {

      /** Copies the members in and out. */
      public Obj {
        members = List.copyOf(members);
      }

      @Override
      public List<Member> members() {
        return List.copyOf(members);
      }
    }

    /** A boolean value. */
    record Bool(boolean value) implements Value {}

    /** A number or null, which no dead-code rule reads. */
    record Other() implements Value {}
  }

  /** Parses {@code text} as a manifest, or returns empty when it is not a JSON object. */
  public static Optional<NodeManifest> parse(final String text) {
    try {
      final Parser parser = new Parser(text);
      final Value value = parser.document();
      return value instanceof Value.Obj object
          ? Optional.of(new NodeManifest(object))
          : Optional.empty();
    } catch (final IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }

  /** Returns the top-level member named {@code key}, or empty. */
  public Optional<Member> member(final String key) {
    return root.members().stream().filter(member -> member.key().equals(key)).findFirst();
  }

  /** Returns the text of the top-level string named {@code key}, or empty. */
  public Optional<String> text(final String key) {
    return member(key)
        .map(Member::value)
        .filter(Value.Text.class::isInstance)
        .map(value -> ((Value.Text) value).value());
  }

  /**
   * Returns the entries of the object named {@code key}, by name, each with the position of its
   * key. An absent key or a non-object value gives an empty list.
   */
  public List<Member> entries(final String key) {
    return member(key)
        .map(Member::value)
        .filter(Value.Obj.class::isInstance)
        .map(value -> ((Value.Obj) value).members())
        .orElse(List.of());
  }

  /** Returns the strings held by the array named {@code key}, or an empty list. */
  public List<String> strings(final String key) {
    final List<String> strings = new ArrayList<>();
    member(key)
        .map(Member::value)
        .filter(Value.Array.class::isInstance)
        .ifPresent(
            value -> {
              for (final Value item : ((Value.Array) value).items()) {
                if (item instanceof Value.Text text) {
                  strings.add(text.value());
                }
              }
            });
    return List.copyOf(strings);
  }

  private static Value literalValue(final String word) {
    return LITERAL_NULL.equals(word)
        ? new Value.Other()
        : new Value.Bool(LITERAL_TRUE.equals(word));
  }

  /** The character reader of one document: where it is, and the reads that move it. */
  private static final class Cursor {

    private static final char QUOTE = '"';
    private static final char NEWLINE = '\n';
    private static final char UNICODE_ESCAPE = 'u';
    private static final List<String> WORDS = List.of(LITERAL_TRUE, LITERAL_FALSE, LITERAL_NULL);
    private static final char BACKSLASH = '\\';
    private static final String NUMBER_CHARS = "+-.0123456789eE";
    private static final Map<Character, Character> SIMPLE_ESCAPES =
        Map.of(
            'n', '\n', 't', '\t', 'r', '\r', 'b', '\b', 'f', '\f', '"', '"', '\\', '\\', '/', '/');

    private final String text;
    private int at;

    Cursor(final String text) {
      this.text = Objects.requireNonNull(text, "text");
    }

    boolean atEnd() {
      return at >= text.length();
    }

    char peek() {
      return text.charAt(at);
    }

    void skipSpace() {
      while (!atEnd() && Character.isWhitespace(peek())) {
        at++;
      }
    }

    boolean eat(final char c) {
      if (!atEnd() && peek() == c) {
        at++;
        return true;
      }
      return false;
    }

    void expect(final char c) {
      skipSpace();
      if (!eat(c)) {
        throw new IllegalArgumentException("expected " + c);
      }
    }

    /** Reads a quoted string, decoding its escapes. */
    String string() {
      expect(QUOTE);
      final StringBuilder out = new StringBuilder();
      while (!atEnd()) {
        final char c = peek();
        at++;
        if (c == QUOTE) {
          return out.toString();
        }
        out.append(c == BACKSLASH ? escaped() : c);
      }
      throw new IllegalArgumentException("unterminated string");
    }

    /** Reads the character after a backslash. */
    private char escaped() {
      if (atEnd()) {
        throw new IllegalArgumentException("unterminated escape");
      }
      final char c = peek();
      at++;
      if (c == UNICODE_ESCAPE) {
        return hex();
      }
      final Character simple = SIMPLE_ESCAPES.get(c);
      if (simple == null) {
        throw new IllegalArgumentException("bad escape");
      }
      return simple;
    }

    private char hex() {
      if (at + HEX_DIGITS > text.length()) {
        throw new IllegalArgumentException("bad unicode escape");
      }
      final char code = (char) Integer.parseInt(text.substring(at, at + HEX_DIGITS), HEX_RADIX);
      at += HEX_DIGITS;
      return code;
    }

    /** Reads {@code true}, {@code false}, {@code null}, or a number. */
    Value literal() {
      final Optional<String> word =
          WORDS.stream().filter(name -> text.startsWith(name, at)).findFirst();
      if (word.isPresent()) {
        at += word.get().length();
        return literalValue(word.get());
      }
      final int start = at;
      while (!atEnd() && NUMBER_CHARS.indexOf(peek()) >= 0) {
        at++;
      }
      if (at == start) {
        throw new IllegalArgumentException("unexpected character");
      }
      return new Value.Other();
    }

    /** The 1-based line and column of the next character. */
    int[] where() {
      int line = 1;
      int lineStart = 0;
      for (int i = 0; i < at; i++) {
        if (text.charAt(i) == NEWLINE) {
          line++;
          lineStart = i + 1;
        }
      }
      return new int[] {line, at - lineStart + 1};
    }
  }

  /** A recursive-descent reader over one document; bad input throws IllegalArgumentException. */
  private static final class Parser {

    private final Cursor cursor;

    Parser(final String text) {
      this.cursor = new Cursor(text);
    }

    /** Reads the whole document: one value, then nothing but space. */
    Value document() {
      final Value value = value();
      cursor.skipSpace();
      if (!cursor.atEnd()) {
        throw new IllegalArgumentException("trailing text");
      }
      return value;
    }

    private Value value() {
      cursor.skipSpace();
      if (cursor.atEnd()) {
        throw new IllegalArgumentException("unexpected end");
      }
      return switch (cursor.peek()) {
        case '{' -> object();
        case '[' -> array();
        case '"' -> new Value.Text(cursor.string());
        default -> cursor.literal();
      };
    }

    private Value.Obj object() {
      cursor.eat('{');
      final List<Member> members = new ArrayList<>();
      cursor.skipSpace();
      if (!cursor.eat('}')) {
        do {
          cursor.skipSpace();
          final int[] where = cursor.where();
          final String key = cursor.string();
          cursor.expect(':');
          members.add(new Member(key, where[0], where[1], value()));
          cursor.skipSpace();
        } while (cursor.eat(','));
        cursor.expect('}');
      }
      return new Value.Obj(members);
    }

    private Value.Array array() {
      cursor.eat('[');
      final List<Value> items = new ArrayList<>();
      cursor.skipSpace();
      if (!cursor.eat(']')) {
        do {
          items.add(value());
          cursor.skipSpace();
        } while (cursor.eat(','));
        cursor.expect(']');
      }
      return new Value.Array(items);
    }
  }
}
