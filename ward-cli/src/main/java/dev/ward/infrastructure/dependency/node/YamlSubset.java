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
    final int keyEnd = YamlScalars.keyEnd(content).orElseThrow(this::unplaced);
    final String key = YamlScalars.unquote(content.substring(0, keyEnd));
    final String rest = content.substring(keyEnd + 1).strip();
    position++;
    if (rest.isEmpty()) {
      target.put(key, block(indent));
    } else if (BLOCK_INDICATOR.matcher(rest).matches()) {
      throw unplacedAt(position);
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
      if (YamlScalars.keyEnd(item).isPresent() && !YamlScalars.quoted(item)) {
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
    return YamlScalars.plain(text);
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
    return unplacedAt(position + 1);
  }

  private static LockfileFormatException unplacedAt(final int line) {
    return new LockfileFormatException("line " + line + " has a shape the reader does not know");
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
}
