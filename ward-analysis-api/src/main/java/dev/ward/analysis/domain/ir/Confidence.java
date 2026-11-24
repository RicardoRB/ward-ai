package dev.ward.analysis.domain.ir;

import java.util.Locale;
import java.util.Optional;

/**
 * How sure a data-flow finding is. A finding is {@link #PROVEN} when the analysis resolved its
 * source, every step and its sink, and {@link #POSSIBLE} when the flow crosses a step that the
 * analysis assumed rather than proved, such as a project call it does not follow.
 */
public enum Confidence {
  /** The source, every step and the sink were resolved. */
  PROVEN,
  /** The flow crosses at least one assumed step. */
  POSSIBLE;

  /** Returns the name used in rule options and reports: the constant in lower case. */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Returns the confidence written as {@code key}, such as {@code "possible"}. */
  public static Optional<Confidence> byKey(final String key) {
    for (final Confidence confidence : values()) {
      if (confidence.key().equals(key)) {
        return Optional.of(confidence);
      }
    }
    return Optional.empty();
  }
}
