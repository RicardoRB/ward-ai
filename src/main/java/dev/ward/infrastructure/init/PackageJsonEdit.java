package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Edits JSON object members in {@code package.json} and {@code turbo.json} text without
 * re-serializing it, so every byte outside the inserted or replaced members is unchanged.
 */
final class PackageJsonEdit {
  private static final String DEV_DEPENDENCIES = "devDependencies";
  private static final String SCRIPTS = "scripts";
  private static final char QUOTE = '"';
  private static final char NEWLINE = '\n';

  private PackageJsonEdit() {}

  /**
   * Returns {@code content} with {@code entries} (name to exact version) appended to {@code
   * devDependencies}, creating the object when absent.
   *
   * @throws WardException if the file is not a multi-line JSON object whose layout can be located
   */
  static String addDevDependencies(final String content, final Map<String, String> entries) {
    return addMembers(content, DEV_DEPENDENCIES, quotedValues(entries));
  }

  /**
   * Returns {@code content} with the {@code scripts} entry {@code name} set to {@code command}: an
   * existing string value is replaced, a missing entry is appended, and a missing {@code scripts}
   * object is created.
   *
   * @throws WardException if the file is not a multi-line JSON object whose layout can be located
   */
  static String withScript(final String content, final String name, final String command) {
    final Optional<JsonSpan> value = JsonMembers.stringValue(JsonText.mask(content), SCRIPTS, name);
    if (value.isPresent()) {
      final JsonSpan span = value.get();
      return content.substring(0, span.open())
          + quoted(command)
          + content.substring(span.close() + 1);
    }
    return addMembers(content, SCRIPTS, Map.of(name, quoted(command)));
  }

  /**
   * Returns {@code content} with {@code members} (name to raw JSON value text) appended to the
   * top-level object {@code key}, creating the object when absent.
   *
   * @throws WardException if the file is not a multi-line JSON object whose layout can be located
   */
  static String addMembers(
      final String content, final String key, final Map<String, String> members) {
    final JsonLayout layout = JsonLayout.of(JsonText.mask(content));
    final String newline = content.contains("\r\n") ? "\r\n" : "\n";
    final Optional<JsonSpan> object = layout.member(key);
    if (object.isEmpty()) {
      return createObject(content, layout, key, members, newline);
    }
    return insertLast(
        content,
        object.get(),
        lines(members, entryIndent(content, layout, object.get()), newline),
        newline);
  }

  /** Adds {@code key} as the last member of the top-level object, holding {@code members}. */
  private static String createObject(
      final String content,
      final JsonLayout layout,
      final String key,
      final Map<String, String> members,
      final String newline) {
    final String indent = layout.indentUnit();
    final String object =
        quoted(key)
            + ": {"
            + newline
            + lines(members, indent + indent, newline)
            + newline
            + indent
            + "}";
    return insertLast(
        content, new JsonSpan(layout.rootOpen(), layout.rootClose()), indent + object, newline);
  }

  /** Inserts {@code text} as the last member of the object {@code object}. */
  private static String insertLast(
      final String content, final JsonSpan object, final String text, final String newline) {
    final int open = object.open();
    int last = object.close() - 1;
    while (last > open && Character.isWhitespace(content.charAt(last))) {
      last--;
    }
    final boolean empty = last == open;
    final String closingIndent = indentOfLine(content, open);
    final String before = content.substring(0, last + 1);
    return before
        + (empty ? "" : ",")
        + newline
        + text
        + newline
        + closingIndent
        + content.substring(object.close());
  }

  /**
   * Returns the indentation of the existing entries of {@code object}, or one level deeper than its
   * key when it is empty.
   */
  private static String entryIndent(
      final String content, final JsonLayout layout, final JsonSpan object) {
    if (content.substring(object.open() + 1, object.close()).isBlank()) {
      return indentOfLine(content, object.open()) + layout.indentUnit();
    }
    final int newline = content.indexOf(NEWLINE, object.open());
    if (newline < 0 || newline > object.close()) {
      throw new WardException(
          "Ward cannot edit this JSON file: an object is written on one line. Put each entry on"
              + " its own line, then run init again.");
    }
    int end = newline + 1;
    while (end < object.close() && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
      end++;
    }
    return content.substring(newline + 1, end);
  }

  private static String lines(
      final Map<String, String> members, final String indent, final String newline) {
    final List<String> lines =
        members.entrySet().stream()
            .map(entry -> indent + quoted(entry.getKey()) + ": " + entry.getValue())
            .toList();
    return String.join("," + newline, lines);
  }

  private static Map<String, String> quotedValues(final Map<String, String> values) {
    final Map<String, String> quoted = new LinkedHashMap<>();
    values.forEach((key, value) -> quoted.put(key, quoted(value)));
    return quoted;
  }

  /** The whitespace that starts the line containing {@code index}. */
  private static String indentOfLine(final String content, final int index) {
    final int start = content.lastIndexOf(NEWLINE, index) + 1;
    int end = start;
    while (end < index && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
      end++;
    }
    return content.substring(start, end);
  }

  private static String quoted(final String value) {
    return QUOTE + value.replace("\\", "\\\\").replace("\"", "\\\"") + QUOTE;
  }
}
