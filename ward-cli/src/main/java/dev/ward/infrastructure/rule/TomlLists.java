package dev.ward.infrastructure.rule;

import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** Reads lists from a rule body that keep their order. */
final class TomlLists {

  private TomlLists() {}

  /**
   * Reads an ordered list, or an empty one when the key is absent; unlike a set it keeps the order.
   */
  static List<String> optional(final TomlParseResult toml, final String key) {
    final TomlArray array = toml.getArray(key);
    if (array == null) {
      return List.of();
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      values.add(TomlValues.stringAt(array, index, key));
    }
    return List.copyOf(values);
  }
}
