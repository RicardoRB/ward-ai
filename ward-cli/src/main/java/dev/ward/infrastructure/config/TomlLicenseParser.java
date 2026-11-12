package dev.ward.infrastructure.config;

import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.config.WardException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Parses the policy of a {@code license} check: {@code deny} or {@code allow}, {@code exceptions},
 * {@code scopes} and {@code unknown}. The gate is native, so a {@code command} is an error.
 */
final class TomlLicenseParser {
  private static final String DENY = "deny";
  private static final String ALLOW = "allow";
  private static final String EXCEPTIONS = "exceptions";
  private static final String SCOPES = "scopes";
  private static final String UNKNOWN = "unknown";
  private static final String FAIL = "fail";
  private static final String WARN = "warn";
  private static final Set<String> VALID_SCOPES =
      Arrays.stream(DependencyScope.values())
          .map(scope -> scope.name().toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());

  private TomlLicenseParser() {}

  /** Returns the settings of a {@code license} check, or {@code null} for any other check. */
  static LicenseSettings parse(
      final String where, final String type, final TomlTable gate, final List<String> command) {
    if (!SupportedQualityGates.LICENSE.equals(type)) {
      return null;
    }
    if (!command.isEmpty()) {
      throw new WardException(
          "'"
              + where
              + ".command' is not supported: the license gate is native and takes no command;"
              + " remove it and set 'uses = \"license\"'");
    }
    if (gate.contains(DENY) && gate.contains(ALLOW)) {
      throw new WardException(
          "'" + where + ".deny' and '" + where + ".allow' cannot both be set; choose one");
    }
    final boolean allow = gate.contains(ALLOW);
    return new LicenseSettings(
        allow ? LicenseSettings.Mode.ALLOW : LicenseSettings.Mode.DENY,
        listed(where, gate, allow),
        gate.contains(EXCEPTIONS)
            ? patterns(where + "." + EXCEPTIONS, gate, EXCEPTIONS)
            : LicenseSettings.DEFAULT_EXCEPTIONS,
        scopes(where + "." + SCOPES, gate),
        unknownFails(where + "." + UNKNOWN, gate));
  }

  /** Returns the allow list, the deny list, or the default deny list, whichever is set. */
  private static List<String> listed(
      final String where, final TomlTable gate, final boolean allow) {
    if (allow) {
      return patterns(where + "." + ALLOW, gate, ALLOW);
    }
    return gate.contains(DENY)
        ? patterns(where + "." + DENY, gate, DENY)
        : LicenseSettings.DEFAULT_DENY;
  }

  private static List<String> patterns(final String key, final TomlTable gate, final String name) {
    final TomlArray array = gate.isArray(name) ? gate.getArray(name) : null;
    if (array == null) {
      throw new WardException("'" + key + "' must be an array of SPDX license identifiers");
    }
    final List<String> patterns = new ArrayList<>();
    for (int i = 0; i < array.size(); i++) {
      final String value = array.isString(i) ? array.getString(i) : null;
      if (value == null || !LicenseSettings.validPattern(value)) {
        throw new WardException(
            "'" + key + "' must contain only SPDX identifiers, optionally ending in '*'");
      }
      patterns.add(value);
    }
    return patterns;
  }

  private static Set<String> scopes(final String key, final TomlTable gate) {
    if (!gate.contains(SCOPES)) {
      return Set.of();
    }
    final TomlArray array = gate.isArray(SCOPES) ? gate.getArray(SCOPES) : null;
    if (array == null) {
      throw new WardException(
          "'" + key + "' must be an array of scope names: " + sorted(VALID_SCOPES));
    }
    final Set<String> scopes = new LinkedHashSet<>();
    for (int i = 0; i < array.size(); i++) {
      scopes.add(scope(key, array, i));
    }
    return scopes;
  }

  private static String scope(final String key, final TomlArray array, final int index) {
    final String value = array.isString(index) ? array.getString(index) : null;
    if (value == null || !VALID_SCOPES.contains(value)) {
      throw new WardException("'" + key + "' must contain only: " + sorted(VALID_SCOPES));
    }
    return value;
  }

  private static List<String> sorted(final Set<String> names) {
    return names.stream().sorted().toList();
  }

  private static boolean unknownFails(final String key, final TomlTable gate) {
    if (!gate.contains(UNKNOWN)) {
      return true;
    }
    final String value = gate.isString(UNKNOWN) ? gate.getString(UNKNOWN) : null;
    if (!FAIL.equals(value) && !WARN.equals(value)) {
      throw new WardException("'" + key + "' must be \"fail\" or \"warn\"");
    }
    return FAIL.equals(value);
  }
}
