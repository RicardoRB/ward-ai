package dev.ward.analysis.domain.rule.metric;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/** A naming convention that a declared identifier must follow. */
enum NameStyle {
  /** Lower case words joined by underscores. */
  SNAKE("snake", "snake_case", "[a-z][a-z0-9_]*"),
  /** Capitalized words with no separator. */
  CAP("cap", "CapWords", "[A-Z][A-Za-z0-9]*"),
  /** Lower camel case, starting with a lower case letter. */
  CAMEL("camel", "lowerCamelCase", "[a-z][A-Za-z0-9]*"),
  /** Snake case for variables, or upper snake case for constants. */
  SNAKE_OR_UPPER(
      "snake-or-upper", "snake_case or UPPER_SNAKE_CASE", "[a-z][a-z0-9_]*|[A-Z][A-Z0-9_]*");

  private final String key;
  private final String description;
  private final Pattern pattern;

  NameStyle(final String key, final String description, final String regex) {
    this.key = key;
    this.description = description;
    this.pattern = Pattern.compile(regex);
  }

  /** Returns the style with {@code key}, if there is one. */
  static Optional<NameStyle> byKey(final String key) {
    return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
  }

  boolean matches(final String name) {
    return pattern.matcher(name).matches();
  }

  String description() {
    return description;
  }
}
