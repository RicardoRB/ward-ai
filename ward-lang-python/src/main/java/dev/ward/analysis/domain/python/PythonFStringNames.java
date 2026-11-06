package dev.ward.analysis.domain.python;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the identifiers in the expression of one f-string replacement field. */
final class PythonFStringNames {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> KEYWORDS =
      Set.of(
          "and", "or", "not", "in", "is", "if", "else", "for", "lambda", "None", "True", "False",
          "await", "yield");

  private PythonFStringNames() {}

  static void identifiers(
      final String token, final int from, final int to, final List<PythonFStrings.Use> uses) {
    final String text = token.substring(from, Math.max(from, to));
    final Matcher matcher = IDENTIFIER.matcher(text);
    while (matcher.find()) {
      final int begin = matcher.start();
      if (!isQuoted(text, begin) && !isAttribute(text, begin) && !isKeyword(matcher.group())) {
        uses.add(new PythonFStrings.Use(matcher.group(), from + begin, from + matcher.end()));
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
}
