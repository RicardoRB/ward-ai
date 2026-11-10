package dev.ward.infrastructure.init.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads the rows of the Node compatibility matrix in {@code cells.csv}, shared by its tests. */
final class NodeMatrixCsv {
  private static final Path CELLS = Path.of("src/test/resources/node-matrix/cells.csv");

  private NodeMatrixCsv() {}

  /** The data rows, without the header. */
  static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).toList();
  }

  /** The rows whose last columns match, so a pinned row ends with its empty version column. */
  static List<String> rowsEndingWith(final String suffix) throws IOException {
    return rows().stream().filter(row -> row.endsWith(suffix)).toList();
  }

  /** The value at {@code index} of a CSV row, or empty when the row omits that column. */
  static String column(final String row, final int index) {
    final String[] columns = row.split(",", -1);
    return index < columns.length ? columns[index] : "";
  }
}
