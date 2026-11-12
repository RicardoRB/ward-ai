package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
    final int keyEnd = keyEnd(content).orElseThrow(this::unplaced);
    final String key = unquote(content.substring(0, keyEnd));
    final String rest = content.substring(keyEnd + 1).strip();
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
      if (keyEnd(item).isPresent() && !quoted(item)) {
        throw unplaced();
      }
      items.add(value(item, position));
      position++;
    }
    return items;
  }

  private static Object value(final String text, final int zeroBasedLine) {
    if (text.startsWith("{") || text.startsWith("[")) {
      return new YamlFlow(text, zeroBasedLine).parse();
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

  /** Returns where the key of {@code key: value} ends, or empty when there is no key. */
  private static OptionalInt keyEnd(final String content) {
    final boolean quoted = quoted(content);
    final int from = quoted ? closingQuote(content, 0, content.charAt(0)) + 1 : 0;
    if (quoted && from == 0) {
      return OptionalInt.empty();
    }
    for (int index = from; index < content.length(); index++) {
      if (isSeparator(content, index)) {
        return OptionalInt.of(index);
      }
      if (quoted) {
        break;
      }
    }
    return OptionalInt.empty();
  }

  private static boolean quoted(final String text) {
    return text.startsWith("'") || text.startsWith("\"");
  }

  /** A colon ends a key only at the end of the line or before a space. */
  private static boolean isSeparator(final String content, final int index) {
    return content.charAt(index) == ':'
        && (index + 1 == content.length() || content.charAt(index + 1) == ' ');
  }

  static int closingQuote(final String text, final int open, final char quote) {
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
}
