package dev.ward.infrastructure.config;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.config.WardException;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses the {@code base} and {@code include} options of an {@code api-compat} gate table. */
final class TomlApiCompatParser {
  private static final String API_COMPAT = "api-compat";
  private static final String BASE = "base";
  private static final String INCLUDE = "include";

  private TomlApiCompatParser() {}

  /** Returns the options of an {@code api-compat} gate, or {@code null} for other gates. */
  static ApiCompatOptions parse(final String where, final String type, final TomlTable gate) {
    if (!API_COMPAT.equals(type)) {
      return null;
    }
    return new ApiCompatOptions(base(where, gate), include(where, gate));
  }

  private static String base(final String where, final TomlTable gate) {
    if (!gate.contains(BASE)) {
      return null;
    }
    final String value = gate.isString(BASE) ? gate.getString(BASE) : null;
    if (value == null || value.isBlank()) {
      throw new WardException("'" + where + "." + BASE + "' must be a non-blank string");
    }
    return value.trim();
  }

  private static List<String> include(final String where, final TomlTable gate) {
    if (!gate.contains(INCLUDE)) {
      return List.of();
    }
    final String key = where + "." + INCLUDE;
    final TomlArray array = gate.isArray(INCLUDE) ? gate.getArray(INCLUDE) : null;
    if (array == null || array.isEmpty()) {
      throw new WardException("'" + key + "' must be a non-empty array of non-blank strings");
    }
    final List<String> prefixes = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      final String prefix = array.isString(i) ? array.getString(i) : null;
      if (prefix == null || prefix.isBlank()) {
        throw new WardException("'" + key + "' must be a non-empty array of non-blank strings");
      }
      prefixes.add(prefix.trim());
    }
    return prefixes;
  }
}
