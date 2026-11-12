package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Parses a lockfile as TOML. */
final class TomlSupport {
  private TomlSupport() {}

  /**
   * Parses {@code text}.
   *
   * @throws LockfileFormatException if it is not valid TOML
   */
  static TomlParseResult parse(final String text) {
    final TomlParseResult parsed = Toml.parse(text);
    if (parsed.hasErrors()) {
      throw new LockfileFormatException("it is not valid TOML");
    }
    return parsed;
  }

  /** Returns the tables of {@code array}, empty when it is null. */
  static List<TomlTable> tables(final TomlArray array) {
    final List<TomlTable> tables = new ArrayList<>();
    if (array != null) {
      for (int index = 0; index < array.size(); index++) {
        tables.add(array.getTable(index));
      }
    }
    return tables;
  }
}
