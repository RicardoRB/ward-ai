package dev.ward.infrastructure.config;

import dev.ward.domain.config.CoverageOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.List;
import org.tomlj.TomlTable;

/** Parses the {@code minimum-branches} and {@code report} of a {@code coverage} check. */
final class TomlCoverageParser {
  private static final String BRANCHES = "minimum-branches";
  private static final long MAXIMUM = 100;

  private TomlCoverageParser() {}

  /**
   * Returns the options of a {@code coverage} check, or {@code null} for another type.
   *
   * @throws WardException if another type sets {@code minimum-branches}
   */
  static CoverageOptions parse(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.COVERAGE.equals(type)) {
      rejectBranches(where, gate);
      return null;
    }
    final List<String> reports = TomlCrapParser.reports(where, gate);
    if (!gate.contains(BRANCHES)) {
      return new CoverageOptions(reports, null);
    }
    final Long value = gate.isLong(BRANCHES) ? gate.getLong(BRANCHES) : null;
    if (value == null || value < 0 || value > MAXIMUM) {
      throw new WardException("'" + where + "." + BRANCHES + "' must be an integer from 0 to 100");
    }
    return new CoverageOptions(reports, value.intValue());
  }

  private static void rejectBranches(final String where, final TomlTable gate) {
    if (gate.contains(BRANCHES)) {
      throw new WardException(
          "'" + where + "." + BRANCHES + "' is supported only for the coverage check");
    }
  }
}
