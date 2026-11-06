package dev.ward.infrastructure.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Turns parsed TOML into plain maps and lists, and shows a value in a message. */
final class TomlTree {
  private TomlTree() {}

  static Map<String, Object> values(final TomlTable rule) {
    final Map<String, Object> values = new LinkedHashMap<>();
    for (final String key : rule.keySet()) {
      values.put(key, value(rule.get(key)));
    }
    return values;
  }

  private static Object value(final Object toml) {
    return toml instanceof TomlArray array ? list(array) : toml;
  }

  static List<Object> list(final Object toml) {
    final List<Object> values = new ArrayList<>();
    if (toml instanceof TomlArray array) {
      for (int index = 0; index < array.size(); index++) {
        values.add(value(array.get(index)));
      }
    }
    return values;
  }

  static String show(final Object value) {
    return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
  }
}
