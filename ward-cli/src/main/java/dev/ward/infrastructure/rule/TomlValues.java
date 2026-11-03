package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;

/** Reads typed parameters from a rule body, reporting a missing or wrong-typed value. */
final class TomlValues {

  private TomlValues() {}

  static String required(final TomlParseResult toml, final String key) {
    final String value = toml.getString(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be a string");
    }
    return value;
  }

  static int integer(final TomlParseResult toml, final String key) {
    final Long value = toml.getLong(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be an integer");
    }
    return toInt(key, value);
  }

  static int optionalInteger(final TomlParseResult toml, final String key, final int fallback) {
    return toml.contains(key) ? integer(toml, key) : fallback;
  }

  static int toInt(final String key, final long value) {
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new RuleConfigurationException("'" + key + "' is out of range");
    }
    return (int) value;
  }

  static Set<String> strings(final TomlParseResult toml, final String key) {
    if (!toml.contains(key)) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    return stringList(toml.getArray(key), key);
  }

  static Set<String> optionalStrings(final TomlParseResult toml, final String key) {
    return toml.contains(key) ? strings(toml, key) : Set.of();
  }

  static Set<String> stringList(final TomlArray array, final String key) {
    if (array == null) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      values.add(stringAt(array, index, key));
    }
    return Set.copyOf(values);
  }

  static String stringAt(final TomlArray array, final int index, final String key) {
    try {
      final String value = array.getString(index);
      if (value != null) {
        return value;
      }
    } catch (final TomlInvalidTypeException exception) {
      throw new RuleConfigurationException("'" + key + "' must hold only strings", exception);
    }
    throw new RuleConfigurationException("'" + key + "' must hold only strings");
  }
}
