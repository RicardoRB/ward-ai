package dev.ward.infrastructure.rule;

import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** Reads the list of specification file names a taint rule names. */
final class SpecPaths {

  private SpecPaths() {}

  /** Returns the classpath paths of the specification files a taint rule names, in order. */
  static List<String> of(final TomlParseResult toml, final String key) {
    final TomlArray array = toml.getArray(key);
    if (array == null || array.isEmpty()) {
      throw new IllegalArgumentException("specs must name at least one specification file");
    }
    final List<String> paths = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isString(index)) {
        throw new IllegalArgumentException("specs must hold file names");
      }
      paths.add(array.getString(index));
    }
    return paths;
  }
}
