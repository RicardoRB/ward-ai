package dev.ward.infrastructure.config;

import dev.ward.domain.config.VulnerabilityOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.vulnerability.Severity;
import org.tomlj.TomlTable;

/**
 * Parses {@code minimum-severity} and {@code max-age-days} of a {@code vulnerabilities} check and
 * rejects them on any other check.
 */
final class TomlVulnerabilitiesParser {
  private static final String SEVERITY = "minimum-severity";
  private static final String AGE = "max-age-days";

  private TomlVulnerabilitiesParser() {}

  static VulnerabilityOptions parse(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.VULNERABILITIES.equals(type)) {
      rejectOn(where, gate, SEVERITY);
      rejectOn(where, gate, AGE);
      return null;
    }
    return new VulnerabilityOptions(severity(where, gate), age(where, gate));
  }

  private static void rejectOn(final String where, final TomlTable gate, final String key) {
    if (gate.contains(key)) {
      throw new WardException(
          "'" + where + "." + key + "' is only supported for the vulnerabilities check");
    }
  }

  private static String severity(final String where, final TomlTable gate) {
    if (!gate.contains(SEVERITY)) {
      return VulnerabilityOptions.DEFAULT_SEVERITY;
    }
    final String value = gate.isString(SEVERITY) ? gate.getString(SEVERITY) : null;
    if (value == null || Severity.parse(value).isEmpty()) {
      throw new WardException(
          "'" + where + "." + SEVERITY + "' must be one of low, moderate, high and critical");
    }
    return value.strip().toLowerCase(java.util.Locale.ROOT);
  }

  private static int age(final String where, final TomlTable gate) {
    if (!gate.contains(AGE)) {
      return VulnerabilityOptions.DEFAULT_MAX_AGE_DAYS;
    }
    final Long value = gate.isLong(AGE) ? gate.getLong(AGE) : null;
    if (value == null || value < 1 || value > Integer.MAX_VALUE) {
      throw new WardException("'" + where + "." + AGE + "' must be a positive whole number");
    }
    return value.intValue();
  }
}
