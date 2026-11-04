package dev.ward.infrastructure.analysis.swift;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the old-style ASCII property list that Xcode writes to {@code project.pbxproj}. Only reads:
 * dictionaries become maps, lists become lists, and strings stay strings, quoted or not.
 */
final class OldStylePlist {

  private static final String UNQUOTED_EXTRA = "_$/:.-+";
  private static final char DICTIONARY_START = '{';
  private static final char LIST_START = '(';
  private static final char SEPARATOR = ',';
  private static final String LINE_COMMENT = "//";
  private static final String BLOCK_COMMENT_START = "/*";
  private static final String BLOCK_COMMENT_END = "*/";

  private final String text;
  private int position;

  private OldStylePlist(final String text) {
    this.text = text;
  }

  /**
   * Parses a property list whose top level is a dictionary, after any leading comment.
   *
   * @throws IllegalArgumentException if the text is not a well-formed property list
   */
  static Map<String, Object> parse(final String text) {
    final OldStylePlist parser = new OldStylePlist(text);
    return parser.dictionary();
  }

  private Map<String, Object> dictionary() {
    expect('{');
    final Map<String, Object> dictionary = new LinkedHashMap<>();
    while (peek() != '}') {
      final String key = string();
      expect('=');
      dictionary.put(key, value());
      expect(';');
    }
    expect('}');
    return dictionary;
  }

  private List<Object> list() {
    expect('(');
    final List<Object> items = new ArrayList<>();
    while (peek() != ')') {
      items.add(value());
      if (peek() == SEPARATOR) {
        position++;
      }
    }
    expect(')');
    return items;
  }

  private Object value() {
    final char next = peek();
    if (next == DICTIONARY_START) {
      return dictionary();
    }
    if (next == LIST_START) {
      return list();
    }
    return string();
  }

  private String string() {
    skipSpace();
    if (position < text.length() && text.charAt(position) == '"') {
      return quoted();
    }
    final int start = position;
    while (position < text.length() && isUnquoted(text.charAt(position))) {
      position++;
    }
    if (start == position) {
      throw failure("a string");
    }
    return text.substring(start, position);
  }

  private String quoted() {
    final StringBuilder value = new StringBuilder();
    position++;
    while (position < text.length() && text.charAt(position) != '"') {
      final char current = text.charAt(position);
      if (current == '\\' && position + 1 < text.length()) {
        position++;
      }
      value.append(text.charAt(position));
      position++;
    }
    expect('"');
    return value.toString();
  }

  private static boolean isUnquoted(final char current) {
    return Character.isLetterOrDigit(current) || UNQUOTED_EXTRA.indexOf(current) >= 0;
  }

  private char peek() {
    skipSpace();
    if (position >= text.length()) {
      throw failure("more input");
    }
    return text.charAt(position);
  }

  private void expect(final char expected) {
    if (peek() != expected) {
      throw failure("'" + expected + "'");
    }
    position++;
  }

  /** Skips whitespace and comments, which Xcode writes between the objects of a project. */
  private void skipSpace() {
    while (position < text.length()) {
      if (Character.isWhitespace(text.charAt(position))) {
        position++;
      } else if (text.startsWith(LINE_COMMENT, position)) {
        final int end = text.indexOf('\n', position);
        position = end < 0 ? text.length() : end + 1;
      } else if (text.startsWith(BLOCK_COMMENT_START, position)) {
        final int end = text.indexOf(BLOCK_COMMENT_END, position);
        if (end < 0) {
          throw failure("the end of a comment");
        }
        position = end + BLOCK_COMMENT_END.length();
      } else {
        return;
      }
    }
  }

  private IllegalArgumentException failure(final String expected) {
    return new IllegalArgumentException("expected " + expected + " at offset " + position);
  }
}
