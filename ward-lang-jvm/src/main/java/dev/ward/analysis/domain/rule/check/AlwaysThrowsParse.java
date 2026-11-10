package dev.ward.analysis.domain.rule.check;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The calls that parse a constant text and so fail on it: the parts of AlwaysThrows that run it.
 */
final class AlwaysThrowsParse {

  private static final String ILLEGAL = "This call will fail at runtime with a %s";
  private static final Map<String, Consumer<CharSequence>> VALIDATORS =
      Map.ofEntries(
          Map.entry("java.time.Duration", Duration::parse),
          Map.entry("java.time.Instant", Instant::parse),
          Map.entry("java.time.LocalDate", LocalDate::parse),
          Map.entry("java.time.LocalDateTime", LocalDateTime::parse),
          Map.entry("java.time.LocalTime", LocalTime::parse),
          Map.entry("java.time.MonthDay", MonthDay::parse),
          Map.entry("java.time.OffsetDateTime", OffsetDateTime::parse),
          Map.entry("java.time.OffsetTime", OffsetTime::parse),
          Map.entry("java.time.Period", Period::parse),
          Map.entry("java.time.Year", Year::parse),
          Map.entry("java.time.YearMonth", YearMonth::parse),
          Map.entry("java.time.ZonedDateTime", ZonedDateTime::parse));

  private AlwaysThrowsParse() {}

  /** Returns why {@code Type.parse(constant)} of a java.time type fails, when it does. */
  static Optional<String> time(final CallSite call) {
    final Consumer<CharSequence> validator = VALIDATORS.get(call.target().declaringType().name());
    if (validator == null || !call.is(call.target().declaringType().name(), "parse", true)) {
      return Optional.empty();
    }
    return call.text(0).flatMap(text -> failure(validator, text));
  }

  /** Returns why {@code UUID.fromString(constant)} fails, when it does. */
  static Optional<String> uuid(final CallSite call) {
    if (!call.is("java.util.UUID", "fromString", true)) {
      return Optional.empty();
    }
    return call.text(0).flatMap(AlwaysThrowsParse::uuidFailure);
  }

  /** Returns why {@code ByteString.fromHex(constant)} fails, when it does. */
  static Optional<String> hex(final CallSite call) {
    if (!call.is("com.google.protobuf.ByteString", "fromHex", true)) {
      return Optional.empty();
    }
    return call.text(0)
        .filter(text -> text.length() % 2 != 0 || !text.matches("[0-9a-fA-F]*"))
        .map(text -> ILLEGAL.formatted("NumberFormatException"));
  }

  private static Optional<String> failure(
      final Consumer<CharSequence> validator, final String text) {
    try {
      validator.accept(text);
      return Optional.empty();
    } catch (DateTimeParseException failed) {
      return Optional.of(ILLEGAL.formatted(failed.getClass().getSimpleName()));
    }
  }

  private static Optional<String> uuidFailure(final String text) {
    try {
      UUID.fromString(text);
      return Optional.empty();
    } catch (IllegalArgumentException failed) {
      return Optional.of(ILLEGAL.formatted(failed.getClass().getSimpleName()));
    }
  }
}
