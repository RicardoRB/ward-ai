package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The fields of a {@code str.format} string, as Python reads them: automatic {@code {}} fields,
 * manual {@code {0}} fields, named {@code {x}} fields, the format specs of the fields, and whether
 * the braces are balanced.
 *
 * @param names the named fields
 * @param manual the manual field indices
 * @param automatic the number of automatic fields
 * @param invalid whether the braces are unbalanced
 * @param specs the format specs of the top-level fields, without the colon
 */
record DotFormatString(
    Set<String> names, Set<Integer> manual, int automatic, boolean invalid, List<String> specs) {

  /** Copies the collections. */
  DotFormatString {
    names = Set.copyOf(names);
    manual = Set.copyOf(manual);
    specs = List.copyOf(specs);
  }

  /** Reads every field of {@code text}, the contents of the string literal. */
  static DotFormatString parse(final String text) {
    final Scanner scanner = new Scanner();
    scanner.scan(text);
    return scanner.result();
  }

  /** One pass over a format string, collecting its fields. */
  private static final class Scanner {

    private static final char OPEN = '{';
    private static final char CLOSE = '}';
    private static final char SPEC = ':';
    private static final char CONVERSION = '!';
    private static final char ATTRIBUTE = '.';
    private static final char INDEX = '[';

    private final Set<String> names = new TreeSet<>();
    private final Set<Integer> manual = new TreeSet<>();
    private final List<String> specs = new ArrayList<>();
    private int automatic;
    private boolean invalid;

    void scan(final String text) {
      int index = 0;
      while (index < text.length() && !invalid) {
        index = step(text, index);
      }
    }

    DotFormatString result() {
      return new DotFormatString(names, manual, automatic, invalid, specs);
    }

    private int step(final String text, final int index) {
      final char current = text.charAt(index);
      if (current == OPEN && escaped(text, index)) {
        return index + 2;
      }
      if (current == CLOSE && escaped(text, index)) {
        return index + 2;
      }
      if (current == OPEN) {
        return field(text, index);
      }
      if (current == CLOSE) {
        invalid = true;
        return index;
      }
      return index + 1;
    }

    private static boolean escaped(final String text, final int index) {
      return index + 1 < text.length() && text.charAt(index + 1) == text.charAt(index);
    }

    private int field(final String text, final int open) {
      final int close = matchingClose(text, open);
      if (close < 0) {
        invalid = true;
        return text.length();
      }
      final String content = text.substring(open + 1, close);
      final int split = firstIndexOf(content, SPEC, CONVERSION);
      name(split < 0 ? content : content.substring(0, split));
      if (split >= 0 && content.charAt(split) == SPEC) {
        specs.add(content.substring(split + 1));
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
}
