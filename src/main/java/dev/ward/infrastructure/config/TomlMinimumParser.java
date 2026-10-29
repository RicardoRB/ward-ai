package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.Set;
import org.tomlj.TomlTable;

/** Parses the optional {@code minimum} score of a mutation gate: an integer from 0 to 100. */
final class TomlMinimumParser {
  private static final String MINIMUM = "minimum";
  private static final long MAXIMUM_SCORE = 100;
  private static final Set<String> MINIMUM_GATES =
      Set.of(SupportedQualityGates.STRYKER, SupportedQualityGates.MUTATION);

  private TomlMinimumParser() {}

  static Integer parse(final String where, final String type, final TomlTable gate) {
    if (!gate.contains(MINIMUM)) {
      return null;
    }
    final String key = where + "." + MINIMUM;
    if (!MINIMUM_GATES.contains(type)) {
      throw new WardException(
          "'" + key + "' is only supported for the stryker and mutation checks");
    }
    final Long value = gate.isLong(MINIMUM) ? gate.getLong(MINIMUM) : null;
    if (value == null || value < 0 || value > MAXIMUM_SCORE) {
      throw new WardException("'" + key + "' must be an integer from 0 to 100");
    }
    return value.intValue();
  }
}
