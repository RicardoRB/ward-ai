package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
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

  /** A name read in an f-string, with where it starts and ends in the token text. */
  record Use(String name, int from, int to) {}

  private static final Pattern PREFIX = Pattern.compile("^[rRbBuU]*[fF][rRbBuU]*['\"]");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> KEYWORDS =
      Set.of(
          "and", "or", "not", "in", "is", "if", "else", "for", "lambda", "None", "True", "False",
          "await", "yield");
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
    int expressionEnd = -1;
    while (index < token.length() && depth > 0) {
      final char current = token.charAt(index);
      if (isQuote(current)) {
        index = skipString(token, index);
        continue;
      }
      depth += depthChange(current);
      if (current != OPEN
          && current != CLOSE
          && depth == 1
          && expressionEnd < 0
          && isSpecStart(token, index)) {
        expressionEnd = index;
      }
      index++;
    }
    final int end = index - 1;
    identifiers(token, start, expressionEnd < 0 ? end : expressionEnd, uses);
    if (expressionEnd >= 0) {
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

  private static void identifiers(
      final String token, final int from, final int to, final List<Use> uses) {
    final String text = token.substring(from, Math.max(from, to));
    final Matcher matcher = IDENTIFIER.matcher(text);
    while (matcher.find()) {
      final int begin = matcher.start();
      if (!isQuoted(text, begin) && !isAttribute(text, begin) && !isKeyword(matcher.group())) {
        uses.add(new Use(matcher.group(), from + begin, from + matcher.end()));
      }
    }
  }

  private static boolean isKeyword(final String name) {
    return KEYWORDS.contains(name);
  }

  private static boolean isAttribute(final String text, final int begin) {
    return begin > 0 && text.charAt(begin - 1) == '.';
  }

  private static boolean isQuoted(final String text, final int index) {
    boolean inside = false;
    char quote = 0;
    for (int position = 0; position < index; position++) {
      final char current = text.charAt(position);
      if (inside && current == quote) {
        inside = false;
      } else if (!inside && (current == '\'' || current == '"')) {
        inside = true;
        quote = current;
      }
    }
    return inside;
  }

  /** The span of {@code from..to} of the token text, inside the token's own span. */
  static Span within(final Span token, final String text, final int from, final int to) {
    final int[] start = position(token, text, from);
    final int[] end = position(token, text, to);
    return new Span(
        start[0], start[1], end[0], end[1], token.startOffset() + from, token.startOffset() + to);
  }

  /** The line and column of the character at {@code index} of the token text. */
  private static int[] position(final Span token, final String text, final int index) {
    int line = token.startLine();
    int column = token.startColumn();
    for (int position = 0; position < index; position++) {
      if (text.charAt(position) == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new int[] {line, column};
  }
}
