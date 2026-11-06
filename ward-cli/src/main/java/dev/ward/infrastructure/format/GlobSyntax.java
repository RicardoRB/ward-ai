package dev.ward.infrastructure.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The brace parts of an EditorConfig glob: the matching brace, the alternatives and number ranges.
 */
final class GlobSyntax {

  private static final char OPEN_BRACE = '{';
  private static final char CLOSE_BRACE = '}';
  private static final char COMMA = ',';
  private static final String RANGE_SEPARATOR = "\\.\\.";
  private static final String NUMBER = "-?\\d{1,9}";
  private static final Pattern NUMBER_PATTERN = Pattern.compile(NUMBER);
  private static final int RANGE_BOUNDS = 2;
  private static final int MAX_DEPTH_STEP = 1;

  private GlobSyntax() {}

  /**
   * Finds the brace that closes the one at {@code open}, counting nested braces.
   *
   * @param glob the glob text
   * @param open the index of the opening brace
   * @return the index of the closing brace, or -1 when it is not closed
   */
  static int matchingBrace(final String glob, final int open) {
    int depth = 0;
    for (int index = open + MAX_DEPTH_STEP; index < glob.length(); index++) {
      final char current = glob.charAt(index);
      if (current == OPEN_BRACE) {
        depth += MAX_DEPTH_STEP;
      } else if (current == CLOSE_BRACE) {
        if (depth == 0) {
          return index;
        }
        depth -= MAX_DEPTH_STEP;
      }
    }
    return -1;
  }

  /**
   * Splits brace content at the commas that are not inside a nested brace.
   *
   * @param inner the text between the braces
   * @return the parts, one for each top-level comma plus one
   */
  static List<String> topLevelParts(final String inner) {
    final List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int index = 0; index < inner.length(); index++) {
      final char current = inner.charAt(index);
      if (current == OPEN_BRACE) {
        depth += MAX_DEPTH_STEP;
      } else if (current == CLOSE_BRACE) {
        depth -= MAX_DEPTH_STEP;
      } else if (current == COMMA && depth == 0) {
        parts.add(inner.substring(start, index));
        start = index + MAX_DEPTH_STEP;
      }
    }
    parts.add(inner.substring(start));
    return parts;
  }

  /**
   * Returns the alternatives of {@code n..m}, or null when the content is not a number range.
   * shortcut: every number in the range becomes one alternative, and bounds are limited to nine
   * digits, so a very wide range makes a large pattern; upgrade to a numeric matcher if that
   * matters.
   *
   * @param inner the text between the braces
   * @return the regex alternation, or null
   */
  static String numericRange(final String inner) {
    final String[] bounds = inner.split(RANGE_SEPARATOR, -1);
    if (bounds.length != RANGE_BOUNDS
        || !NUMBER_PATTERN.matcher(bounds[0]).matches()
        || !NUMBER_PATTERN.matcher(bounds[1]).matches()) {
      return null;
    }
    final int first = Integer.parseInt(bounds[0]);
    final int second = Integer.parseInt(bounds[1]);
    final List<String> numbers = new ArrayList<>();
    for (int value = Math.min(first, second); value <= Math.max(first, second); value++) {
      numbers.add(Integer.toString(value));
    }
    return numbers.stream().collect(Collectors.joining("|", "(?:", ")"));
  }
}
