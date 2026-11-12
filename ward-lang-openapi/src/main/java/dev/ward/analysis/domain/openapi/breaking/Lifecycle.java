package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Reads the lifecycle extensions of an operation: {@code deprecated}, {@code x-sunset}. */
final class Lifecycle {

  static final String SUNSET = "x-sunset";
  static final String STABILITY = "x-stability-level";

  private Lifecycle() {}

  /** Tells whether the operation says {@code deprecated: true}. */
  static boolean deprecated(final DocNode operation) {
    return operation.get("deprecated").map(node -> "true".equals(node.text())).orElse(false);
  }

  /** Returns the {@code x-sunset} node of the operation, if any. */
  static Optional<DocNode> sunset(final DocNode operation) {
    return operation.get(SUNSET);
  }

  /**
   * Parses a sunset as an RFC 3339 date or date-time and returns its day; empty when it is neither.
   * A sunset that is not a string is not a date.
   */
  static Optional<LocalDate> day(final DocNode sunset) {
    final String text = sunset.text();
    try {
      return Optional.of(LocalDate.parse(text));
    } catch (final DateTimeParseException notADate) {
      return dateTime(text);
    }
  }

  private static Optional<LocalDate> dateTime(final String text) {
    try {
      return Optional.of(OffsetDateTime.parse(text).toLocalDate());
    } catch (final DateTimeParseException notADateTime) {
      return Optional.empty();
    }
  }

  /** The text of a sunset node as written, for a message. */
  static String written(final DocNode sunset) {
    return sunset.text();
  }
}
