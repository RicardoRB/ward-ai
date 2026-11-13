package dev.ward.infrastructure.config;

import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Parses the keys of a {@code secrets} check: {@code scan-history}, {@code history-since} and
 * {@code exclude-paths}. No other key beyond the common ones is accepted, and {@code history-since}
 * needs {@code scan-history}.
 */
final class TomlSecretsParser {
  private static final String SCAN_HISTORY = "scan-history";
  private static final String HISTORY_SINCE = "history-since";
  private static final String EXCLUDE_PATHS = "exclude-paths";
  private static final Set<String> KEYS =
      Set.of("uses", "enabled", "profiles", "paths", SCAN_HISTORY, HISTORY_SINCE, EXCLUDE_PATHS);

  private TomlSecretsParser() {}

  /** Returns the options of {@code gate}, or {@link SecretsOptions#NONE} for another type. */
  static SecretsOptions parse(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.SECRETS.equals(type)) {
      return SecretsOptions.NONE;
    }
    for (final String key : gate.keySet()) {
      if (!KEYS.contains(key)) {
        throw new WardException("'" + where + "." + key + "' is not a key of a secrets check");
      }
    }
    final boolean scan = scanHistory(where, gate);
    final Optional<LocalDate> since = since(where, gate, scan);
    return new SecretsOptions(scan, since, excludePaths(where, gate));
  }

  private static boolean scanHistory(final String where, final TomlTable gate) {
    if (!gate.contains(SCAN_HISTORY)) {
      return false;
    }
    if (!gate.isBoolean(SCAN_HISTORY)) {
      throw new WardException("'" + where + "." + SCAN_HISTORY + "' must be true or false");
    }
    return Boolean.TRUE.equals(gate.getBoolean(SCAN_HISTORY));
  }

  private static Optional<LocalDate> since(
      final String where, final TomlTable gate, final boolean scan) {
    if (!gate.contains(HISTORY_SINCE)) {
      return Optional.empty();
    }
    final String key = where + "." + HISTORY_SINCE;
    if (!scan) {
      throw new WardException("'" + key + "' needs '" + where + "." + SCAN_HISTORY + " = true'");
    }
    try {
      return Optional.of(
          LocalDate.parse(gate.isString(HISTORY_SINCE) ? gate.getString(HISTORY_SINCE) : ""));
    } catch (final DateTimeParseException exception) {
      throw new WardException("'" + key + "' must be an ISO date such as 2026-01-01", exception);
    }
  }

  private static List<String> excludePaths(final String where, final TomlTable gate) {
    if (!gate.contains(EXCLUDE_PATHS)) {
      return List.of();
    }
    final TomlArray array = gate.isArray(EXCLUDE_PATHS) ? gate.getArray(EXCLUDE_PATHS) : null;
    final String key = where + "." + EXCLUDE_PATHS;
    if (array == null) {
      throw new WardException("'" + key + "' must be a list of globs");
    }
    final List<String> globs = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isString(index)) {
        throw new WardException("'" + key + "' must hold only strings");
      }
      globs.add(array.getString(index));
    }
    return globs;
  }
}
