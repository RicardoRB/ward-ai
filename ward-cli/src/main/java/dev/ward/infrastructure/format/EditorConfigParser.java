package dev.ward.infrastructure.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the INI syntax of an {@code .editorconfig} file. Lines Ward does not understand are
 * skipped.
 */
final class EditorConfigParser {

  private static final String ROOT_KEY = "root";
  private static final String TRUE = "true";

  private EditorConfigParser() {}

  /**
   * Parses the text of one {@code .editorconfig} file.
   *
   * @param directory the folder that holds the file, relative to the project root
   * @param text the file text, without a byte-order mark
   * @return the parsed file
   */
  static EditorConfigFile parse(final String directory, final String text) {
    final List<String> headers = new ArrayList<>();
    final List<Map<String, String>> bodies = new ArrayList<>();
    boolean root = false;
    for (final String rawLine : text.split("\\R")) {
      final String line = rawLine.strip();
      if (isHeader(line)) {
        headers.add(line.substring(1, line.length() - 1));
        bodies.add(new LinkedHashMap<>());
      } else if (isProperty(line)) {
        if (bodies.isEmpty()) {
          root = root || isRootSetting(line);
        } else {
          bodies.getLast().put(keyOf(line), valueOf(line));
        }
      }
    }
    return new EditorConfigFile(directory, root, sections(headers, bodies));
  }

  private static boolean isHeader(final String line) {
    return line.startsWith("[") && line.endsWith("]");
  }

  private static boolean isProperty(final String line) {
    return !line.isEmpty()
        && !line.startsWith("#")
        && !line.startsWith(";")
        && line.indexOf('=') > 0;
  }

  private static boolean isRootSetting(final String line) {
    return ROOT_KEY.equals(keyOf(line)) && TRUE.equalsIgnoreCase(valueOf(line));
  }

  private static List<EditorConfigSection> sections(
      final List<String> headers, final List<Map<String, String>> bodies) {
    final List<EditorConfigSection> sections = new ArrayList<>();
    for (int index = 0; index < headers.size(); index++) {
      final String header = headers.get(index);
      sections.add(
          new EditorConfigSection(header, EditorConfigGlob.compile(header), bodies.get(index)));
    }
    return sections;
  }

  private static String keyOf(final String line) {
    return line.substring(0, line.indexOf('=')).strip().toLowerCase(Locale.ROOT);
  }

  private static String valueOf(final String line) {
    return line.substring(line.indexOf('=') + 1).strip();
  }
}
