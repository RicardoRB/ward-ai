package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads typed parameters from a rule body, reporting a missing or wrong-typed value. */
public final class TomlValues {

  private TomlValues() {}

  /**
   * Parses a rule body.
   *
   * @throws RuleConfigurationException if the body is not valid TOML
   */
  public static TomlParseResult parse(final String body) {
    final TomlParseResult toml = Toml.parse(body);
    if (toml.hasErrors()) {
      throw new RuleConfigurationException("the rule body is not valid TOML");
    }
    return toml;
  }

  /** Reads a required string. */
  public static String required(final TomlParseResult toml, final String key) {
    final String value = toml.getString(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be a string");
    }
    return value;
  }

  /** Reads a required non-negative integer. */
  public static int integer(final TomlParseResult toml, final String key) {
    final Long value = toml.getLong(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be an integer");
    }
    return toInt(key, value);
  }

  /** Reads an integer, or {@code fallback} when the key is absent. */
  public static int optionalInteger(
      final TomlParseResult toml, final String key, final int fallback) {
    return toml.contains(key) ? integer(toml, key) : fallback;
  }

  /** Narrows {@code value} to an int, rejecting a negative or oversized one. */
  public static int toInt(final String key, final long value) {
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new RuleConfigurationException("'" + key + "' is out of range");
    }
    return (int) value;
  }

  /** Reads a required list of strings as a set. */
  public static Set<String> strings(final TomlParseResult toml, final String key) {
    if (!toml.contains(key)) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    return stringList(toml.getArray(key), key);
  }

  /** Reads a list of strings as a set, or an empty set when the key is absent. */
  public static Set<String> optionalStrings(final TomlParseResult toml, final String key) {
    return toml.contains(key) ? strings(toml, key) : Set.of();
  }

  /** Reads {@code array} as a set of strings. */
  public static Set<String> stringList(final TomlArray array, final String key) {
    if (array == null) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      values.add(stringAt(array, index, key));
    }
    return Set.copyOf(values);
  }

  /** Reads the string at {@code index} of {@code array}. */
  public static String stringAt(final TomlArray array, final int index, final String key) {
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

  /** Reads a table of kind to sibling kind; a missing table is empty. */
  public static Map<String, String> kindMap(final TomlParseResult toml, final String key) {
    final TomlTable table = toml.getTable(key);
    final Map<String, String> map = new HashMap<>();
    if (table != null) {
      table.toMap().forEach((kind, sibling) -> map.put(kind, String.valueOf(sibling)));
    }
    return Map.copyOf(map);
  }
}
