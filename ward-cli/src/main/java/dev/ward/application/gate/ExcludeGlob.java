package dev.ward.application.gate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an {@code exclude-paths} glob into a regular expression over a project-relative path with
 * {@code /} separators. {@code *} matches within a directory, {@code **} across directories, {@code
 * ?} one character, and {@code {a,b}} either text. Character classes are not supported.
 */
final class ExcludeGlob {
  private static final Pattern TOKEN = Pattern.compile("\\*\\*|[*?{},\\[\\]]|.", Pattern.DOTALL);

  private static final Map<String, String> TRANSLATIONS =
      Map.of("**", ".*", "*", "[^/]*", "?", "[^/]", "{", "(?:", "}", ")");
  private static final Map<String, Integer> DEPTH = Map.of("{", 1, "}", -1);

  private static final String COMMA = ",";

  private ExcludeGlob() {}

  /**
   * Compiles {@code glob}.
   *
   * @throws IllegalArgumentException if the glob has unbalanced braces or a character class
   */
  static Pattern compile(final String glob) {
    final StringBuilder regex = new StringBuilder();
    final Matcher tokens = TOKEN.matcher(glob);
    int depth = 0;
    while (tokens.find()) {
      final String token = tokens.group();
      regex.append(translate(token, depth));
      depth += DEPTH.getOrDefault(token, 0);
      if (depth < 0) {
        throw new IllegalArgumentException("unbalanced braces");
      }
    }
    if (depth > 0) {
      throw new IllegalArgumentException("unbalanced braces");
    }
    return Pattern.compile(regex.toString());
  }

  private static String translate(final String token, final int depth) {
    if ("[".equals(token) || "]".equals(token)) {
      throw new IllegalArgumentException("character classes are not supported");
    }
    if (COMMA.equals(token)) {
      return depth > 0 ? "|" : COMMA;
    }
    return TRANSLATIONS.getOrDefault(token, Pattern.quote(token));
  }
}
