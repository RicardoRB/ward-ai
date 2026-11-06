package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One configurable option of a rule, declared on its row next to the rule:
 *
 * <pre>{@code
 * options.max = { type = "integer", min = 1 }
 * options.level = { type = "enum", values = ["a", "b"], parameter = "mode" }
 * }</pre>
 *
 * <p>The key is the {@code name} users write in {@code ward-rules.toml}. {@code type} is a {@link
 * RuleOptionType} key; {@code min} and {@code max} bound an integer; {@code values} lists the texts
 * of an enum; {@code parameter} is the body key the option overrides and defaults to the name. The
 * default of an option is the value already in the rule body, so a rule that is not configured
 * behaves as before. See {@link RuleOptions} for how a value reaches the body.
 *
 * @param name the option name, lower case with {@code -}
 * @param type the value type
 * @param parameter the body key the option overrides
 * @param min the smallest integer allowed, or {@link Long#MIN_VALUE} when unbounded
 * @param max the largest integer allowed, or {@link Long#MAX_VALUE} when unbounded
 * @param values the allowed texts of an enum option, empty for other types
 */
public record RuleOption(
    String name, RuleOptionType type, String parameter, long min, long max, List<String> values) {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

  /**
   * Validates the declaration and copies the values.
   *
   * @throws IllegalArgumentException if the name is not lower case with {@code -}, the range is
   *     empty, or an enum lists no values
   */
  public RuleOption {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("option name must be lower case with '-', found: " + name);
    }
    parameter = parameter == null || parameter.isBlank() ? name : parameter;
    if (min > max) {
      throw new IllegalArgumentException("option '" + name + "' has min greater than max");
    }
    values = List.copyOf(values);
    if (type == RuleOptionType.ENUM && values.isEmpty()) {
      throw new IllegalArgumentException("enum option '" + name + "' needs values");
    }
  }

  /** Declares an option without bounds or values whose parameter has the name of the option. */
  public RuleOption(final String name, final RuleOptionType type) {
    this(name, type, null, Long.MIN_VALUE, Long.MAX_VALUE, List.of());
  }

  /**
   * Declares an integer option that is at least {@code min}.
   *
   * @param name the option name, which is also the body key it overrides
   * @param min the smallest value allowed
   */
  public static RuleOption integer(final String name, final long min) {
    return new RuleOption(name, RuleOptionType.INTEGER, null, min, Long.MAX_VALUE, List.of());
  }

  /**
   * Returns why {@code value} is not allowed, or empty when it is. A value read from TOML is a
   * {@link Long}, {@link Boolean}, {@link String} or a {@link List} of strings.
   */
  public Optional<String> problem(final Object value) {
    if (type == RuleOptionType.INTEGER) {
      return integerProblem(value);
    }
    if (type == RuleOptionType.REGEX) {
      return regexProblem(value);
    }
    if (type == RuleOptionType.STRING_LIST) {
      return listProblem(value);
    }
    if (type == RuleOptionType.ENUM) {
      return enumProblem(value);
    }
    return simpleProblem(value);
  }

  private Optional<String> simpleProblem(final Object value) {
    final boolean boolType = type == RuleOptionType.BOOLEAN;
    final boolean fits = boolType ? value instanceof Boolean : value instanceof String;
    return fits ? Optional.empty() : wrong(boolType ? "true or false" : "a string", value);
  }

  private Optional<String> integerProblem(final Object value) {
    final String expected = "an integer" + range();
    if (!(value instanceof Long number)) {
      return wrong(expected, value);
    }
    return number < min || number > max ? wrong(expected, value) : Optional.empty();
  }

  private String range() {
    if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) {
      return " between " + min + " and " + max;
    }
    if (min != Long.MIN_VALUE) {
      return " >= " + min;
    }
    return max == Long.MAX_VALUE ? "" : " <= " + max;
  }

  private Optional<String> regexProblem(final Object value) {
    if (!(value instanceof String text)) {
      return wrong("a regular expression string", value);
    }
    try {
      Pattern.compile(text);
      return Optional.empty();
    } catch (final PatternSyntaxException exception) {
      return Optional.of("expected a valid regular expression: " + exception.getDescription());
    }
  }

  private Optional<String> listProblem(final Object value) {
    if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
      return Optional.empty();
    }
    return wrong("a list of strings", value);
  }

  private Optional<String> enumProblem(final Object value) {
    return value instanceof String text && values.contains(text)
        ? Optional.empty()
        : wrong("one of " + values, value);
  }

  private static Optional<String> wrong(final String expected, final Object value) {
    final String got = value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
    return Optional.of("expected " + expected + ", got " + got);
  }
}
