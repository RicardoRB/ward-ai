package dev.ward.infrastructure.config;

import dev.ward.domain.config.CommandTypeInference;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGateDefinitions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses one {@code [checks.<name>]} table. */
final class TomlGateParser {
  private static final String PROFILES = "profiles";
  private static final String USES = "uses";
  private static final String PARTIAL = "partial";

  private TomlGateParser() {}

  /** Parses the check {@code name}, whose table is {@code gate}. */
  static GateConfiguration parse(final String name, final TomlTable gate) {
    final String where = TomlConfigurationReader.CHECKS + "." + name;
    rejectLegacyPartial(where, gate);
    final String uses = uses(where, gate);
    final List<String> command = TomlCommandParser.parse(where, gate, uses);
    final String type = type(uses, command);
    final boolean enabled = enabled(where, gate);
    return new GateConfiguration(
        name,
        type,
        enabled,
        command,
        profiles(where, gate),
        TomlPartialParser.parse(where, gate),
        TomlOpenSpecRoot.parse(where, type, gate),
        TomlOpenApiParser.parse(where, type, gate, enabled),
        TomlMinimumParser.parse(where, type, gate));
  }

  private static void rejectLegacyPartial(final String where, final TomlTable gate) {
    if (gate.contains(PARTIAL)) {
      throw new WardException(
          "'"
              + where
              + "."
              + PARTIAL
              + "' is no longer supported; use '"
              + where
              + ".paths' with"
              + " 'match' and 'full' instead");
    }
  }

  private static String uses(final String where, final TomlTable gate) {
    if (!gate.contains(USES)) {
      return null;
    }
    final String key = where + "." + USES;
    final String uses = gate.isString(USES) ? gate.getString(USES) : null;
    if (uses == null || QualityGateDefinitions.find(uses).isEmpty()) {
      throw new WardException(
          "'"
              + key
              + "' must be one of the supported types: "
              + QualityGateDefinitions.all().stream()
                  .map(QualityGateDefinitions.Definition::id)
                  .toList());
    }
    return uses;
  }

  /** The declared {@code uses}, or the type the command implies. */
  private static String type(final String uses, final List<String> command) {
    return uses != null ? uses : CommandTypeInference.infer(command);
  }

  private static boolean enabled(final String where, final TomlTable gate) {
    if (!gate.contains("enabled")) {
      return true;
    }
    final Boolean value = gate.isBoolean("enabled") ? gate.getBoolean("enabled") : null;
    if (value == null) {
      throw new WardException("'" + where + ".enabled' must be true or false");
    }
    return value;
  }

  private static Set<String> profiles(final String where, final TomlTable gate) {
    if (!gate.contains(PROFILES)) {
      return Set.of(WardConfiguration.DEFAULT_PROFILE);
    }
    final String key = where + ".profiles";
    if (!gate.isArray(PROFILES)) {
      throw new WardException("'" + key + "' must be a non-empty array of non-blank strings");
    }
    final TomlArray array = gate.getArray(PROFILES);
    if (array == null || array.isEmpty()) {
      throw new WardException("'" + key + "' must not be empty");
    }
    final Set<String> profiles = new LinkedHashSet<>();
    for (int i = 0; i < array.size(); i++) {
      profiles.add(profile(array, i, key));
    }
    return profiles;
  }

  private static String profile(final TomlArray array, final int index, final String key) {
    final String profile = array.isString(index) ? array.getString(index) : null;
    if (profile == null || profile.isBlank()) {
      throw new WardException("'" + key + "' must contain only non-blank strings");
    }
    return profile.trim();
  }
}
