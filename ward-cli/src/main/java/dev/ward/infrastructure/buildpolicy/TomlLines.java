package dev.ward.infrastructure.buildpolicy;

import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/** Reads the line of a TOML key or array element, {@code 0} when the parser reports none. */
public final class TomlLines {
  private TomlLines() {}

  /** Returns the 1-based line of the key at {@code path} in {@code table}. */
  public static int of(final TomlTable table, final List<String> path) {
    return line(table.inputPositionOf(path));
  }

  /** Returns the 1-based line of element {@code index} of {@code array}. */
  public static int of(final TomlArray array, final int index) {
    return line(array.inputPositionOf(index));
  }

  private static int line(final TomlPosition position) {
    return position == null ? 0 : position.line();
  }
}
