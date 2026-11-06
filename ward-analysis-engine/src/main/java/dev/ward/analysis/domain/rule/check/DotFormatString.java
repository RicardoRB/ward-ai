package dev.ward.analysis.domain.rule.check;

import java.util.Set;
import java.util.TreeSet;

/**
 * The fields of a {@code str.format} string, as Python reads them: automatic {@code {}} fields,
 * manual {@code {0}} fields, named {@code {x}} fields, nested fields in a format spec, and whether
 * the braces are balanced.
 */
final class DotFormatString {

  private static final char OPEN = '{';
  private static final char CLOSE = '}';
  private static final char SPEC = ':';
  private static final char CONVERSION = '!';
  private static final char ATTRIBUTE = '.';
  private static final char INDEX = '[';

  private final Set<String> names = new TreeSet<>();
  private final Set<Integer> manual = new TreeSet<>();
  private int automatic;
  private boolean invalid;

  private DotFormatString() {}

  /** Reads every field of {@code text}, the contents of the string literal. */
  static DotFormatString parse(final String text) {
    final DotFormatString parsed = new DotFormatString();
    parsed.scan(text);
    return parsed;
  }

  /** Whether the braces are unbalanced. */
  boolean invalid() {
    return invalid;
  }

  /** The number of automatic fields. */
  int automatic() {
    return automatic;
  }

  /** The manual field indices. */
  Set<Integer> manual() {
    return Set.copyOf(manual);
  }

  /** The named fields. */
  Set<String> names() {
    return Set.copyOf(names);
  }

  private void scan(final String text) {
    int index = 0;
    while (index < text.length() && !invalid) {
      final char current = text.charAt(index);
      if (current == OPEN && isEscape(text, index, OPEN)) {
        index += 2;
      } else if (current == CLOSE && isEscape(text, index, CLOSE)) {
        index += 2;
      } else if (current == OPEN) {
        index = field(text, index);
      } else if (current == CLOSE) {
        invalid = true;
      } else {
        index++;
      }
    }
  }

  private static boolean isEscape(final String text, final int index, final char brace) {
    return index + 1 < text.length() && text.charAt(index + 1) == brace;
  }

  private int field(final String text, final int open) {
    final int close = matchingClose(text, open);
    if (close < 0) {
      invalid = true;
      return text.length();
    }
    final String content = text.substring(open + 1, close);
    final int split = firstIndexOf(content, SPEC, CONVERSION);
    final String head = split < 0 ? content : content.substring(0, split);
    name(head);
    if (split >= 0 && content.charAt(split) == SPEC) {
      scan(content.substring(split + 1));
    }
    return close + 1;
  }

  private void name(final String text) {
    final int end = firstIndexOf(text, ATTRIBUTE, INDEX);
    final String name = end < 0 ? text : text.substring(0, end);
    if (name.isEmpty()) {
      automatic++;
    } else if (name.chars().allMatch(Character::isDigit)) {
      manual.add(Integer.parseInt(name));
    } else {
      names.add(name);
    }
  }

  private static int firstIndexOf(final String text, final char first, final char second) {
    for (int index = 0; index < text.length(); index++) {
      if (text.charAt(index) == first || text.charAt(index) == second) {
        return index;
      }
    }
    return -1;
  }

  private static int matchingClose(final String text, final int open) {
    int depth = 0;
    for (int index = open; index < text.length(); index++) {
      if (text.charAt(index) == OPEN) {
        depth++;
      } else if (text.charAt(index) == CLOSE) {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return -1;
  }
}
