package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.Set;

/**
 * The value-based classes of the JDK, whose instances have no identity: the primitive wrappers and
 * the classes that Java documents as value-based. A class that only carries the {@code
 * jdk.internal.ValueBased} annotation is not known here, so it is not reported.
 */
final class ValueClasses {

  private static final Set<String> NAMES =
      Set.of(
          "java.lang.Boolean",
          "java.lang.Byte",
          "java.lang.Character",
          "java.lang.Double",
          "java.lang.Float",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Short",
          "java.lang.ProcessHandle",
          "java.lang.Runtime.Version",
          "java.time.Duration",
          "java.time.Instant",
          "java.time.LocalDate",
          "java.time.LocalDateTime",
          "java.time.LocalTime",
          "java.time.MonthDay",
          "java.time.OffsetDateTime",
          "java.time.OffsetTime",
          "java.time.Period",
          "java.time.Year",
          "java.time.YearMonth",
          "java.time.ZoneId",
          "java.time.ZoneOffset",
          "java.time.ZonedDateTime",
          "java.time.chrono.HijrahDate",
          "java.time.chrono.JapaneseDate",
          "java.time.chrono.MinguoDate",
          "java.time.chrono.ThaiBuddhistDate",
          "java.util.Optional",
          "java.util.OptionalDouble",
          "java.util.OptionalInt",
          "java.util.OptionalLong");

  private ValueClasses() {}

  /** Tells whether {@code type} is a value-based class that is not an array. */
  static boolean is(final TypeRef type) {
    return type != null
        && type.arrayDepth() == 0
        && !type.primitive()
        && NAMES.contains(type.name());
  }
}
