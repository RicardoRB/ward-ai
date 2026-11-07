package dev.ward.analysis.domain.rule;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The options of a {@code duplicate-code} rule, read from its body. The body is TOML, one {@code
 * key = value} line per option, and a key the body leaves out keeps its default.
 *
 * @param minTokens the fewest tokens a copy must have to be reported
 * @param ignoreLiterals whether literals compare equal whatever their text
 * @param ignoreIdentifiers whether identifiers compare equal whatever their name
 * @param ignoreAnnotations whether annotations are left out of the comparison
 */
public record DuplicateCodeOptions(
    int minTokens, boolean ignoreLiterals, boolean ignoreIdentifiers, boolean ignoreAnnotations) {

  /** The copy length that PMD CPD and jscpd default to. */
  public static final int DEFAULT_MIN_TOKENS = 100;

  private static final int MIN_LENGTH = 1;

  private static final Pattern LINE =
      Pattern.compile("^\\s*([a-z-]+)\\s*=\\s*(\\S+)\\s*$", Pattern.MULTILINE);
  private static final String MIN_TOKENS = "min-tokens";
  private static final String IGNORE_LITERALS = "ignore-literals";
  private static final String IGNORE_IDENTIFIERS = "ignore-identifiers";
  private static final String IGNORE_ANNOTATIONS = "ignore-annotations";

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if the minimum is below one
   */
  public DuplicateCodeOptions {
    if (minTokens < MIN_LENGTH) {
      throw new IllegalArgumentException("min-tokens must be at least 1");
    }
  }

  /**
   * Reads the options from a rule body. Values are validated by the option declarations of the
   * rule, so a value that is not a number is only reported as a malformed body.
   *
   * @throws NullPointerException if the body is missing
   * @throws IllegalArgumentException if a number option is not a number
   */
  public static DuplicateCodeOptions parse(final String body) {
    final Map<String, String> values = new HashMap<>();
    final Matcher line = LINE.matcher(Objects.requireNonNull(body, "body"));
    while (line.find()) {
      values.put(line.group(1), line.group(2));
    }
    return new DuplicateCodeOptions(
        number(values, MIN_TOKENS, DEFAULT_MIN_TOKENS),
        flag(values, IGNORE_LITERALS),
        flag(values, IGNORE_IDENTIFIERS),
        flag(values, IGNORE_ANNOTATIONS));
  }

  private static int number(
      final Map<String, String> values, final String key, final int fallback) {
    final String text = values.get(key);
    return text == null ? fallback : Integer.parseInt(text);
  }

  private static boolean flag(final Map<String, String> values, final String key) {
    return Boolean.parseBoolean(values.get(key));
  }
}
