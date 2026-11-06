package dev.ward.analysis.domain.python;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the names an f-string reads. The grammar keeps an f-string as one token, so the replacement
 * fields are scanned as text: a name is an identifier in a field that is not an attribute, not in a
 * quoted string and not a keyword.
 *
 * <p>shortcut: a field is scanned lexically, so a name in a nested f-string is found only when its
 * quotes differ from the outer string's; upgrade by parsing the field with the expression grammar.
 */
final class PythonFStrings {

  private static final Pattern PREFIX = Pattern.compile("^[rRbBuU]*[fF][rRbBuU]*['\"]");
  private static final int NONE = Integer.MAX_VALUE;
  private static final char OPEN = '{';
  private static final char CLOSE = '}';

  private PythonFStrings() {}

  /** Reports whether the string token is an f-string. */
  static boolean isFString(final String token) {
    return PREFIX.matcher(token).find();
  }

  /** The names the replacement fields of the f-string token read, in order. */
  static List<Use> names(final String token) {
    final List<Use> uses = new ArrayList<>();
    int index = 0;
    while (index < token.length()) {
      final char current = token.charAt(index);
      if (current == OPEN && index + 1 < token.length() && token.charAt(index + 1) == OPEN) {
        index += 2;
      } else if (current == OPEN) {
        index = field(token, index + 1, uses);
      } else {
        index++;
      }
    }
    return uses;
  }

  /** Scans one field from just after its opening brace; returns the index after its closing. */
  private static int field(final String token, final int start, final List<Use> uses) {
    int depth = 1;
    int index = start;
    int expressionEnd = NONE;
    while (index < token.length() && depth > 0) {
      final char current = token.charAt(index);
      if (isQuote(current)) {
        index = skipString(token, index);
        continue;
      }
      depth += depthChange(current);
      if (depth == 1 && expressionEnd == NONE && isSpecStart(token, index)) {
        expressionEnd = index;
      }
      index++;
    }
    final int end = index - 1;
    PythonFStringNames.identifiers(token, start, Math.min(expressionEnd, end), uses);
    if (expressionEnd < end) {
      nestedFields(token, expressionEnd, end, uses);
    }
    return index;
  }

  private static boolean isQuote(final char current) {
    return current == '\'' || current == '"';
  }

  private static int depthChange(final char current) {
    if (current == OPEN) {
      return 1;
    }
    return current == CLOSE ? -1 : 0;
  }

  /** A format spec may hold replacement fields of its own, such as {@code {x:>{width}}}. */
  private static void nestedFields(
      final String token, final int from, final int to, final List<Use> uses) {
    int index = from;
    while (index < to) {
      index = token.charAt(index) == OPEN ? field(token, index + 1, uses) : index + 1;
    }
  }

  /** A {@code :} or {@code !} outside brackets ends the expression; {@code !=} does not. */
  private static boolean isSpecStart(final String token, final int index) {
    final char current = token.charAt(index);
    final boolean notEqual =
        current == '!' && index + 1 < token.length() && token.charAt(index + 1) == '=';
    return (current == ':' || current == '!') && !notEqual && bracketDepth(token, index) == 0;
  }

  private static int bracketDepth(final String token, final int end) {
    int depth = 0;
    final int field = token.lastIndexOf(OPEN, end);
    for (int index = field + 1; index < end; index++) {
      final char current = token.charAt(index);
      depth += "([".indexOf(current) >= 0 ? 1 : 0;
      depth -= ")]".indexOf(current) >= 0 ? 1 : 0;
    }
    return depth;
  }

  private static int skipString(final String token, final int open) {
    final char quote = token.charAt(open);
    int index = open + 1;
    while (index < token.length() && token.charAt(index) != quote) {
      index++;
    }
    return index + 1;
  }

  /** A name read in an f-string, with where it starts and ends in the token text. */
  record Use(String name, int from, int to) {}
}
