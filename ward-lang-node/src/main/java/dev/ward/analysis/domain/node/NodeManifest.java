package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code package.json} read for the keys that dead-code rules need, each with the line and column
 * where its key is written, so a finding can point into the manifest. The reader is a small JSON
 * parser: a file it cannot parse yields no manifest, so a rule that needs it reports nothing.
 */
public final class NodeManifest {

  /** One member of an object: its key, where the key is written, and its value. */
  public record Member(String key, int line, int column, Json value) {

    /** Validates the parts. @throws NullPointerException if a part is missing */
    public Member {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(value, "value");
    }
  }

  /** A JSON value, with the members of objects kept in the order the file writes them. */
  public sealed interface Json {

    /** A string value. */
    record Text(String value) implements Json {}

    /** An array value. */
    record Array(List<Json> items) implements Json {}

    /** An object value. */
    record Obj(List<Member> members) implements Json {}

    /** A number, boolean or null, which no dead-code rule reads. */
    record Other() implements Json {}
  }

  private final Json.Obj root;

  private NodeManifest(final Json.Obj root) {
    this.root = root;
  }

  /** Parses {@code text} as a manifest, or returns empty when it is not a JSON object. */
  public static Optional<NodeManifest> parse(final String text) {
    try {
      final Parser parser = new Parser(text);
      final Json value = parser.value();
      parser.skipSpace();
      if (parser.hasMore() || !(value instanceof Json.Obj obj)) {
        return Optional.empty();
      }
      return Optional.of(new NodeManifest(obj));
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
        .filter(Json.Text.class::isInstance)
        .map(value -> ((Json.Text) value).value());
  }

  /**
   * Returns the entries of the object named {@code key}, by name, each with the position of its
   * key. An absent key or a non-object value gives an empty list.
   */
  public List<Member> entries(final String key) {
    return member(key)
        .map(Member::value)
        .filter(Json.Obj.class::isInstance)
        .map(value -> ((Json.Obj) value).members())
        .orElse(List.of());
  }

  /** Returns the strings held by the array named {@code key}, or an empty list. */
  public List<String> strings(final String key) {
    final List<String> strings = new ArrayList<>();
    member(key)
        .map(Member::value)
        .filter(Json.Array.class::isInstance)
        .ifPresent(
            value ->
                ((Json.Array) value)
                    .items()
                    .forEach(
                        item -> {
                          if (item instanceof Json.Text text) {
                            strings.add(text.value());
                          }
                        }));
    return List.copyOf(strings);
  }

  /** The reader of one JSON document; it throws {@link IllegalArgumentException} on bad input. */
  private static final class Parser {
    private final String text;
    private final int[] at = {0};

    Parser(final String text) {
      this.text = Objects.requireNonNull(text, "text");
    }

    boolean hasMore() {
      return at[0] < text.length();
    }

    void skipSpace() {
      while (hasMore() && Character.isWhitespace(text.charAt(at[0]))) {
        at[0]++;
      }
    }

    Json value() {
      skipSpace();
      if (!hasMore()) {
        throw new IllegalArgumentException("unexpected end");
      }
      final char c = text.charAt(at[0]);
      return switch (c) {
        case '{' -> object();
        case '[' -> array();
        case '"' -> new Json.Text(string());
        default -> scalar();
      };
    }

    private Json.Obj object() {
      at[0]++;
      final List<Member> members = new ArrayList<>();
      skipSpace();
      if (take('}')) {
        return new Json.Obj(members);
      }
      do {
        skipSpace();
        final int[] position = position();
        final String key = string();
        skipSpace();
        expect(':');
        members.add(new Member(key, position[0], position[1], value()));
        skipSpace();
      } while (take(','));
      expect('}');
      return new Json.Obj(members);
    }

    private Json.Array array() {
      at[0]++;
      final List<Json> items = new ArrayList<>();
      skipSpace();
      if (take(']')) {
        return new Json.Array(items);
      }
      do {
        items.add(value());
        skipSpace();
      } while (take(','));
      expect(']');
      return new Json.Array(items);
    }

    private String string() {
      expect('"');
      final StringBuilder out = new StringBuilder();
      while (hasMore()) {
        final char c = text.charAt(at[0]++);
        if (c == '"') {
          return out.toString();
        }
        if (c == '\\') {
          out.append(escape());
        } else {
          out.append(c);
        }
      }
      throw new IllegalArgumentException("unterminated string");
    }

    private char escape() {
      if (!hasMore()) {
        throw new IllegalArgumentException("unterminated escape");
      }
      final char c = text.charAt(at[0]++);
      return switch (c) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case 'r' -> '\r';
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'u' -> unicode();
        case '"', '\\', '/' -> c;
        default -> throw new IllegalArgumentException("bad escape");
      };
    }

    private char unicode() {
      if (at[0] + 4 > text.length()) {
        throw new IllegalArgumentException("bad unicode escape");
      }
      final char code = (char) Integer.parseInt(text.substring(at[0], at[0] + 4), 16);
      at[0] += 4;
      return code;
    }

    private Json scalar() {
      for (final String literal : List.of("true", "false", "null")) {
        if (text.startsWith(literal, at[0])) {
          at[0] += literal.length();
          return new Json.Other();
        }
      }
      final int start = at[0];
      while (hasMore() && "+-.0123456789eE".indexOf(text.charAt(at[0])) >= 0) {
        at[0]++;
      }
      if (at[0] == start) {
        throw new IllegalArgumentException("unexpected character");
      }
      return new Json.Other();
    }

    /** The 1-based line and column of the next character. */
    private int[] position() {
      int line = 1;
      int column = 1;
      for (int i = 0; i < at[0]; i++) {
        if (text.charAt(i) == '\n') {
          line++;
          column = 1;
        } else {
          column++;
        }
      }
      return new int[] {line, column};
    }

    private boolean take(final char c) {
      if (hasMore() && text.charAt(at[0]) == c) {
        at[0]++;
        return true;
      }
      return false;
    }

    private void expect(final char c) {
      skipSpace();
      if (!take(c)) {
        throw new IllegalArgumentException("expected " + c);
      }
    }
  }
}
