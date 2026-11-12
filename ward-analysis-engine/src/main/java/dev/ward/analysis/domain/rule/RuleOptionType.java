package dev.ward.analysis.domain.rule;

import java.util.Locale;
import java.util.Optional;

/** The value types of a rule option, as written in the {@code type} key of its declaration. */
public enum RuleOptionType {
  /** A whole number, optionally bounded by {@code min} and {@code max}. */
  INTEGER,
  /** A number, whole or fractional, at least {@code min}. */
  NUMBER,
  /** {@code true} or {@code false}. */
  BOOLEAN,
  /** Any text. */
  STRING,
  /** Text that is a valid regular expression, compiled during validation. */
  REGEX,
  /** A list of texts. */
  STRING_LIST,
  /** One of the texts listed in {@code values}. */
  ENUM;

  /** Returns the type written as {@code key}, such as {@code "string-list"}. */
  public static Optional<RuleOptionType> byKey(final String key) {
    for (final RuleOptionType type : values()) {
      if (type.key().equals(key)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  /** Returns the name used in rule rows: the constant in lower case with {@code -}. */
  public String key() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
