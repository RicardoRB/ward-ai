package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the YAML that pnpm and Yarn Berry write into lockfiles, and nothing else: block mappings
 * indented with spaces, block sequences of scalars, quoted or plain keys and scalars, and flow
 * mappings and sequences on one line. A line it cannot place fails with its number, so a lockfile
 * shape change is an error and never a silent misreading.
 *
 * <p>Maps become {@code LinkedHashMap<String, Object>}, sequences {@code List<Object>} and scalars
 * {@code String}; a quoted scalar loses its quotes and an empty value is an empty string.
 */
final class YamlSubset {
  private static final String SEQUENCE_ITEM = "- ";
  private static final Pattern BLOCK_INDICATOR = Pattern.compile("[|>][+-]?|[&*][A-Za-z].*");

  private final List<String> lines;
  private int position;

  private YamlSubset(final List<String> lines) {
    this.lines = lines;
  }

  /**
   * Parses {@code text} into a map.
   *
   * @throws LockfileFormatException with the line number of the first line it cannot place
   */
  static Map<String, Object> parse(final String text) {
    final YamlSubset yaml = new YamlSubset(text.lines().toList());
    final Map<String, Object> root = new LinkedHashMap<>();
    yaml.skipBlank();
    while (yaml.position < yaml.lines.size()) {
      if (indent(yaml.lines.get(yaml.position)) != 0) {
        throw yaml.unplaced();
      }
      yaml.entry(root, 0);
      yaml.skipBlank();
    }
    return root;
  }

  private void entry(final Map<String, Object> target, final int indent) {
    final String line = lines.get(position);
    final String content = line.substring(indent);
    final int[] split = splitKey(content);
    if (split == null) {
      throw unplaced();
    }
    final String key = unquote(content.substring(0, split[0]));
    final String rest = content.substring(split[1]).strip();
    position++;
    if (rest.isEmpty()) {
      target.put(key, block(indent));
    } else if (isBlockScalar(rest)) {
      throw unplacedPrevious();
    } else {
      target.put(key, value(rest, position - 1));
    }
  }

  /** Returns the nested block under a key at {@code indent}, or an empty text if none follows. */
  private Object block(final int indent) {
    skipBlank();
    if (position >= lines.size()) {
      return "";
    }
    final String line = lines.get(position);
    final int inner = indent(line);
    final boolean sequenceAtSameIndent = inner == indent && line.startsWith(SEQUENCE_ITEM, indent);
    if (inner <= indent && !sequenceAtSameIndent) {
      return "";
    }
    return line.startsWith(SEQUENCE_ITEM, inner) ? sequence(inner) : mapping(inner);
  }

  private Map<String, Object> mapping(final int indent) {
    final Map<String, Object> map = new LinkedHashMap<>();
    while (true) {
      skipBlank();
      if (position >= lines.size() || indent(lines.get(position)) != indent) {
        break;
      }
      entry(map, indent);
    }
    if (position < lines.size() && indent(lines.get(position)) > indent) {
      throw unplaced();
    }
    return map;
  }

  private List<Object> sequence(final int indent) {
    final List<Object> items = new ArrayList<>();
    while (true) {
      skipBlank();
      if (position >= lines.size()
          || indent(lines.get(position)) != indent
          || !lines.get(position).startsWith(SEQUENCE_ITEM, indent)) {
        break;
      }
      final String item = lines.get(position).substring(indent + SEQUENCE_ITEM.length()).strip();
      if (splitKey(item) != null && !item.startsWith("'") && !item.startsWith("\"")) {
        throw unplaced();
      }
      items.add(value(item, position));
      position++;
    }
    return items;
  }

  private static Object value(final String text, final int zeroBasedLine) {
    if (text.startsWith("{") || text.startsWith("[")) {
      return new Flow(text, zeroBasedLine).parse();
    }
    return scalar(text);
  }

  private static String scalar(final String text) {
    final String stripped = stripComment(text);
    return unquote(stripped);
  }

  private static boolean isBlockScalar(final String rest) {
    return BLOCK_INDICATOR.matcher(rest).matches();
  }

  private void skipBlank() {
    while (position < lines.size()) {
      final String stripped = lines.get(position).strip();
      if (!stripped.isEmpty() && !stripped.startsWith("#")) {
        return;
      }
      position++;
    }
  }

  private LockfileFormatException unplaced() {
    return new LockfileFormatException(
        "line " + (position + 1) + " has a shape the reader does not know");
  }

  private LockfileFormatException unplacedPrevious() {
    return new LockfileFormatException(
        "line " + position + " has a shape the reader does not know");
  }

  private static int indent(final String line) {
    int count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    if (count < line.length() && line.charAt(count) == '\t') {
      throw new LockfileFormatException("a line is indented with a tab");
    }
    return count;
  }

  /** Returns {end of key, start of value} for {@code key: value}, or {@code null}. */
  private static int[] splitKey(final String content) {
    int index = 0;
    if (content.startsWith("'") || content.startsWith("\"")) {
      final char quote = content.charAt(0);
      index = closingQuote(content, 0, quote);
      if (index < 0) {
        return null;
      }
      index++;
      return index < content.length()
              && content.charAt(index) == ':'
              && (index + 1 == content.length() || content.charAt(index + 1) == ' ')
          ? new int[] {index, index + 1}
          : null;
    }
    while (index < content.length()) {
      if (content.charAt(index) == ':'
          && (index + 1 == content.length() || content.charAt(index + 1) == ' ')) {
        return new int[] {index, index + 1};
      }
      index++;
    }
    return null;
  }

  private static int closingQuote(final String text, final int open, final char quote) {
    int index = open + 1;
    while (index < text.length()) {
      final char current = text.charAt(index);
      if (current == '\\' && quote == '"') {
        index += 2;
        continue;
      }
      if (current == quote) {
        if (quote == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
          index += 2;
          continue;
        }
        return index;
      }
      index++;
    }
    return -1;
  }

  private static String stripComment(final String text) {
    if (text.startsWith("'") || text.startsWith("\"")) {
      final int close = closingQuote(text, 0, text.charAt(0));
      return close < 0 ? text : text.substring(0, close + 1);
    }
    final int comment = text.indexOf(" #");
    return comment < 0 ? text : text.substring(0, comment).strip();
  }

  /** Returns {@code value} as a map, or an empty map when it is not one. */
  static Map<String, Object> map(final Object value) {
    final Map<String, Object> copy = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> source) {
      source.forEach((key, item) -> copy.put(String.valueOf(key), item));
    }
    return copy;
  }

  /** Returns {@code value} as text, or an empty text when it is not a scalar. */
  static String scalar(final Object value) {
    return value instanceof String text ? text : "";
  }

  static String unquote(final String text) {
    if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
      return text.substring(1, text.length() - 1).replace("''", "'");
    }
    if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
      return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return text;
  }

  /** A flow collection on one line: {@code {a: b, c: d}} or {@code [x, y]}. */
  private static final class Flow {
    private final String text;
    private final int line;
    private int at;

    Flow(final String text, final int zeroBasedLine) {
      this.text = text;
      this.line = zeroBasedLine;
    }

    Object parse() {
      final Object value = element();
      skipSpaces();
      if (at != text.length()) {
        throw fail();
      }
      return value;
    }

    private Object element() {
      skipSpaces();
      if (at >= text.length()) {
        throw fail();
      }
      return switch (text.charAt(at)) {
        case '{' -> map();
        case '[' -> list();
        default -> plain();
      };
    }

    private Map<String, Object> map() {
      final Map<String, Object> map = new LinkedHashMap<>();
      at++;
      skipSpaces();
      if (peek() == '}') {
        at++;
        return map;
      }
      while (true) {
        final String key = unquote(token(true));
        skipSpaces();
        if (peek() != ':') {
          throw fail();
        }
        at++;
        map.put(key, element());
        skipSpaces();
        final char next = peek();
        at++;
        if (next == '}') {
          return map;
        }
        if (next != ',') {
          throw fail();
        }
      }
    }

    private List<Object> list() {
      final List<Object> list = new ArrayList<>();
      at++;
      skipSpaces();
      if (peek() == ']') {
        at++;
        return list;
      }
      while (true) {
        list.add(element());
        skipSpaces();
        final char next = peek();
        at++;
        if (next == ']') {
          return list;
        }
        if (next != ',') {
          throw fail();
        }
      }
    }

    private String plain() {
      return unquote(token(false));
    }

    private String token(final boolean key) {
      skipSpaces();
      final int start = at;
      if (peek() == '\'' || peek() == '"') {
        final int close = closingQuote(text, at, text.charAt(at));
        if (close < 0) {
          throw fail();
        }
        at = close + 1;
        return text.substring(start, at);
      }
      while (at < text.length()) {
        final char current = text.charAt(at);
        if (current == ',' || current == '}' || current == ']' || (key && current == ':')) {
          break;
        }
        at++;
      }
      return text.substring(start, at).strip();
    }

    private char peek() {
      return at < text.length() ? text.charAt(at) : '\0';
    }

    private void skipSpaces() {
      while (at < text.length() && text.charAt(at) == ' ') {
        at++;
      }
    }

    private LockfileFormatException fail() {
      return new LockfileFormatException(
          "line " + (line + 1) + " has a shape the reader does not know");
    }
  }
}
