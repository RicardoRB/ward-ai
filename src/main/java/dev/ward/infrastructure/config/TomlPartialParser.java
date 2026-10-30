package dev.ward.infrastructure.config;

import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.config.WardException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses the optional {@code [checks.<name>.paths]} table of one check. */
final class TomlPartialParser {
  private static final String PATHS = "paths";
  private static final String MATCH = "match";
  private static final String INPUTS = "inputs";
  private static final String CONFIG = "config";
  private static final String FULL = "full";
  private static final String ARGS = "args";
  private static final String ITEM = "item";
  private static final String COMMAND = "command";
  private static final String SEPARATOR = "separator";
  private static final Set<String> KEYS = Set.of(MATCH, FULL, ARGS, ITEM, COMMAND, SEPARATOR);

  private TomlPartialParser() {}

  /** Returns the check's paths settings, or {@code null} when it declares none. */
  static PartialGateConfiguration parse(final String where, final TomlTable gate) {
    if (!gate.contains(PATHS)) {
      return null;
    }
    final String key = where + "." + PATHS;
    final TomlTable table = gate.isTable(PATHS) ? gate.getTable(PATHS) : null;
    if (table == null) {
      throw new WardException("'" + key + "' must be a table");
    }
    rejectUnknownKeys(key, table);
    requires(table, key, ARGS, MATCH);
    requires(table, key, COMMAND, ARGS);
    return new PartialGateConfiguration(
        strings(table, key, MATCH),
        strings(table, key, FULL),
        strings(table, key, ARGS),
        item(table, key),
        string(table, key, SEPARATOR),
        strings(table, key, COMMAND));
  }

  /** Names the replacement of a renamed key, and rejects any other unknown key. */
  private static void rejectUnknownKeys(final String key, final TomlTable table) {
    for (final String name : table.keySet()) {
      if (INPUTS.equals(name)) {
        throw new WardException("'" + key + ".inputs' was renamed; use '" + key + ".match'");
      }
      if (CONFIG.equals(name)) {
        throw new WardException("'" + key + ".config' was renamed; use '" + key + ".full'");
      }
      if (!KEYS.contains(name)) {
        throw new WardException("'" + key + "." + name + "' is not a supported key");
      }
    }
  }

  private static void requires(
      final TomlTable table, final String key, final String name, final String required) {
    if (table.contains(name) && !table.contains(required)) {
      throw new WardException("'" + key + "." + name + "' requires '" + key + "." + required + "'");
    }
  }

  /** An {@code item} without a placeholder would pass the same text for every edited path. */
  private static String item(final TomlTable table, final String key) {
    final String item = string(table, key, ITEM);
    if (item != null
        && !item.contains("{path}")
        && !item.contains("{name}")
        && !item.contains("{package}")) {
      throw new WardException("'" + key + ".item' must contain {path}, {name} or {package}");
    }
    return item;
  }

  private static List<String> strings(final TomlTable table, final String key, final String name) {
    if (!table.contains(name)) {
      return List.of();
    }
    final TomlArray array = table.isArray(name) ? table.getArray(name) : null;
    if (array == null) {
      throw new WardException("'" + key + "." + name + "' must be an array of strings");
    }
    final List<String> values = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      values.add(element(array, i, key + "." + name));
    }
    return values;
  }

  private static String element(final TomlArray array, final int index, final String key) {
    final String value = array.isString(index) ? array.getString(index) : null;
    if (value == null || value.isBlank()) {
      throw new WardException("'" + key + "' must contain only non-blank strings");
    }
    return value.trim();
  }

  private static String string(final TomlTable table, final String key, final String name) {
    if (!table.contains(name)) {
      return null;
    }
    if (!table.isString(name)) {
      throw new WardException("'" + key + "." + name + "' must be a string");
    }
    return table.getString(name);
  }
}
