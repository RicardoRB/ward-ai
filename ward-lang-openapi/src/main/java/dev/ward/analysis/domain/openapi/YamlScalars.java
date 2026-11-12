package dev.ward.analysis.domain.openapi;

import java.util.Map;
import java.util.regex.Pattern;

/** Reads the quoted scalars and resolves the type of plain scalars by the YAML 1.2 core schema. */
final class YamlScalars {

  private static final Pattern INTEGER = Pattern.compile("[-+]?[0-9]+|0o[0-7]+|0x[0-9a-fA-F]+");
  private static final Pattern FLOAT =
      Pattern.compile("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?");
  private static final Pattern INFINITY = Pattern.compile("[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)");
  private static final Pattern NULL = Pattern.compile("null|Null|NULL|~");
  private static final Pattern BOOLEAN = Pattern.compile("true|True|TRUE|false|False|FALSE");
  private static final int HEX = 16;
  private static final int HEX_2 = 2;
  private static final int HEX_4 = 4;
  private static final int HEX_8 = 8;
  private static final char SINGLE = '\'';
  private static final char DOUBLE = '"';
  private static final char BACKSLASH = '\\';
  private static final Map<Character, Character> SIMPLE_ESCAPES =
      Map.ofEntries(
          Map.entry('n', '\n'),
          Map.entry('t', '\t'),
          Map.entry('\t', '\t'),
          Map.entry('r', '\r'),
          Map.entry('b', '\b'),
          Map.entry('f', '\f'),
          Map.entry('0', '\0'),
          Map.entry('a', '\u0007'),
          Map.entry('e', '\u001B'),
          Map.entry('v', '\u000B'),
          Map.entry('/', '/'),
          Map.entry(DOUBLE, DOUBLE),
          Map.entry(BACKSLASH, BACKSLASH),
          Map.entry(' ', ' '),
          Map.entry('N', '\u0085'),
          Map.entry('_', ' '),
          Map.entry('L', ' '),
          Map.entry('P', ' '));
  private static final Map<Character, Integer> HEX_ESCAPES =
      Map.of('x', HEX_2, 'u', HEX_4, 'U', HEX_8);

  private final YamlCursor cursor;
  private final YamlLines lines;

  YamlScalars(final YamlCursor cursor, final YamlLines lines) {
    this.cursor = cursor;
    this.lines = lines;
  }

  /** The kind a plain scalar has: null, boolean, number or string. */
  static String kindOf(final String plain) {
    final String kind;
    if (plain.isEmpty() || NULL.matcher(plain).matches()) {
      kind = YamlSyntax.NULL;
    } else if (BOOLEAN.matcher(plain).matches()) {
      kind = YamlSyntax.BOOLEAN;
    } else if (INTEGER.matcher(plain).matches()
        || FLOAT.matcher(plain).matches()
        || INFINITY.matcher(plain).matches()) {
      kind = YamlSyntax.NUMBER;
    } else {
      kind = YamlSyntax.STRING;
    }
    return kind;
  }

  /** Reads a single-quoted scalar from the opening quote; leaves the cursor after the closing. */
  String singleQuoted() {
    final int start = cursor.pos();
    cursor.advance(1);
    final StringBuilder text = new StringBuilder();
    while (true) {
      if (cursor.eof()) {
        throw cursor.fail("unterminated single-quoted string", start);
      }
      final char c = cursor.peek();
      if (c == SINGLE && cursor.charAt(cursor.pos() + 1) == SINGLE) {
        text.append(SINGLE);
        cursor.advance(2);
      } else if (c == SINGLE) {
        cursor.advance(1);
        return text.toString();
      } else if (YamlCursor.isBreak(c)) {
        fold(text, start);
      } else {
        text.append(c);
        cursor.advance(1);
      }
    }
  }

  /** Reads a double-quoted scalar from the opening quote; leaves the cursor after the closing. */
  String doubleQuoted() {
    final int start = cursor.pos();
    cursor.advance(1);
    final StringBuilder text = new StringBuilder();
    while (true) {
      if (cursor.eof()) {
        throw cursor.fail("unterminated double-quoted string", start);
      }
      final char c = cursor.peek();
      if (c == DOUBLE) {
        cursor.advance(1);
        return text.toString();
      } else if (c == BACKSLASH) {
        escape(text, start);
      } else if (YamlCursor.isBreak(c)) {
        fold(text, start);
      } else {
        text.append(c);
        cursor.advance(1);
      }
    }
  }

  /** Folds the line breaks inside a quoted scalar: one break is a space, more are newlines. */
  private void fold(final StringBuilder text, final int start) {
    int length = text.length();
    while (length > 0 && (text.charAt(length - 1) == ' ' || text.charAt(length - 1) == '\t')) {
      length--;
    }
    text.setLength(length);
    int breaks = 0;
    while (YamlCursor.isBreak(cursor.peek())) {
      lines.skipBreak();
      breaks++;
      lines.skipSpaces();
    }
    if (cursor.eof()) {
      throw cursor.fail("unterminated quoted string", start);
    }
    text.append(breaks == 1 ? " " : "\n".repeat(breaks - 1));
  }

  private void escape(final StringBuilder text, final int start) {
    final int at = cursor.pos();
    final char code = cursor.charAt(at + 1);
    cursor.advance(2);
    final Character simple = SIMPLE_ESCAPES.get(code);
    final Integer digits = HEX_ESCAPES.get(code);
    if (simple != null) {
      text.append(simple.charValue());
    } else if (digits != null) {
      hex(text, digits, at);
    } else if (YamlCursor.isBreak(code)) {
      cursor.moveTo(at + 1);
      lines.skipBreak();
      lines.skipSpaces();
    } else {
      throw cursor.fail("unknown escape sequence in a double-quoted string", at);
    }
    if (cursor.eof()) {
      throw cursor.fail("unterminated double-quoted string", start);
    }
  }

  private void hex(final StringBuilder text, final int digits, final int at) {
    final int from = cursor.pos();
    int value = 0;
    for (int index = from; index < from + digits; index++) {
      final int digit = Character.digit(cursor.charAt(index), HEX);
      if (digit < 0) {
        throw cursor.fail("invalid escape sequence in a double-quoted string", at);
      }
      value = value * HEX + digit;
    }
    if (!Character.isValidCodePoint(value)) {
      throw cursor.fail("invalid escape sequence in a double-quoted string", at);
    }
    text.appendCodePoint(value);
    cursor.moveTo(from + digits);
  }
}
