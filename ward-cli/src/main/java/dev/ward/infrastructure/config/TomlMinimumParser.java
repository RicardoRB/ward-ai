package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.Set;
import org.tomlj.TomlTable;

/**
 * Parses the {@code minimum} percentage of a mutation or Swift coverage gate: an integer from 0 to
 * 100, optional for mutation gates and required for {@code swift-coverage}.
 */
final class TomlMinimumParser {
  private static final String MINIMUM = "minimum";
  private static final long MAXIMUM_SCORE = 100;
  private static final Set<String> MINIMUM_GATES =
      Set.of(
          SupportedQualityGates.STRYKER,
          SupportedQualityGates.MUTATION,
          SupportedQualityGates.MUTMUT,
          SupportedQualityGates.SWIFT_COVERAGE,
          SupportedQualityGates.ANDROID_COVERAGE);
  private static final Set<String> REQUIRED_MINIMUM =
      Set.of(SupportedQualityGates.SWIFT_COVERAGE, SupportedQualityGates.ANDROID_COVERAGE);

  private TomlMinimumParser() {}

  static Integer parse(final String where, final String type, final TomlTable gate) {
    final String key = where + "." + MINIMUM;
    if (!gate.contains(MINIMUM)) {
      return absent(key, type);
    }
    if (!MINIMUM_GATES.contains(type)) {
      throw new WardException(
          "'"
              + key
              + "' is only supported for the stryker, mutation, mutmut, swift-coverage and android-coverage"
              + " checks");
    }
    final Long value = gate.isLong(MINIMUM) ? gate.getLong(MINIMUM) : null;
    if (value == null || value < 0 || value > MAXIMUM_SCORE) {
      throw new WardException("'" + key + "' must be an integer from 0 to 100");
    }
    return value.intValue();
  }

  /** A mutation gate may leave {@code minimum} out; a coverage gate has nothing to check. */
  private static Integer absent(final String key, final String type) {
    if (REQUIRED_MINIMUM.contains(type)) {
      throw new WardException("'" + key + "' is required for " + type + " checks");
    }
    return null;
  }
}
