package dev.ward.domain.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits the {@code include} and {@code exclude} arrays of the {@code [select]} table of {@code
 * ward-rules.toml} as text, so comments, blank lines and the other keys keep their place. It
 * changes nothing else; the caller checks the result with a TOML reader before it writes.
 */
public final class RuleSelectionEdit {
  /** The array of rules the project turns on. */
  public static final String INCLUDE = "include";

  /** The array of rules the project turns off. */
  public static final String EXCLUDE = "exclude";

  /** The text of a file that does not exist yet: the version and an empty {@code [select]}. */
  public static final String NEW_FILE = "version = 1\n\n[select]\n";

  private static final String NEWLINE = "\n";
  private static final char QUOTE = '"';

  private RuleSelectionEdit() {}

  /**
   * Adds each id to {@code include} and removes it from {@code exclude}.
   *
   * @param text the file text, or {@code null} when the file does not exist
   * @param ids the rule ids
   * @return the new text
   */
  public static String enable(final String text, final List<String> ids) {
    return change(text, ids, INCLUDE, EXCLUDE);
  }

  /**
   * Adds each id to {@code exclude} and removes it from {@code include}.
   *
   * @param text the file text, or {@code null} when the file does not exist
   * @param ids the rule ids
   * @return the new text
   */
  public static String disable(final String text, final List<String> ids) {
    return change(text, ids, EXCLUDE, INCLUDE);
  }

  private static String change(
      final String text, final List<String> ids, final String target, final String source) {
    final String start = text == null || text.isBlank() ? NEW_FILE : text;
    final List<String> lines = new ArrayList<>(List.of(start.split(NEWLINE, -1)));
    SelectTable.ensure(lines);
    for (final String id : ids) {
      final String token = QUOTE + id + QUOTE;
      RuleArrayEdit.remove(lines, source, token);
      RuleArrayEdit.add(lines, target, token);
    }
    return String.join(NEWLINE, lines);
  }
}
