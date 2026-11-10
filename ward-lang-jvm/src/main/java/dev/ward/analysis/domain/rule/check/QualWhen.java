package dev.ward.analysis.domain.rule.check;

import java.util.Locale;
import java.util.Optional;

/** The {@code when} of a JSR-305 type qualifier annotation: how sure it is that a value has it. */
enum QualWhen {
  /** The value always has the qualifier. */
  ALWAYS,
  /** The value may have it. */
  MAYBE,
  /** The value never has it. */
  NEVER,
  /** Nothing is known. */
  UNKNOWN;

  /** Reads a {@code when} from the text of an annotation value such as {@code When.NEVER}. */
  static Optional<QualWhen> parse(final String text) {
    final String name = text.substring(text.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    for (final QualWhen when : values()) {
      if (when.name().equals(name)) {
        return Optional.of(when);
      }
    }
    return Optional.empty();
  }
}
