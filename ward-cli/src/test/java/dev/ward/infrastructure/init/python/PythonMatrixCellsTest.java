package dev.ward.infrastructure.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The offline check of the Python matrix rows: every supported version and manager has a cell. */
class PythonMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/python-matrix/cells.csv");
  private static final List<String> VERSIONS = List.of("3.11", "3.12", "3.13", "3.14");
  private static final List<String> MANAGERS = List.of("uv", "poetry", "pip");
  private static final String NEWEST = "3.14";
  private static final int FIXTURE_COLUMN = 3;

  @Test
  void everySupportedVersionHasAStrictCellForEveryManager() throws IOException {
    final List<String> rows = rows();

    for (final String version : VERSIONS) {
      for (final String manager : MANAGERS) {
        assertThat(rows)
            .as("strict cell for Python %s on %s", version, manager)
            .anyMatch(row -> row.startsWith(version + "," + manager + ",strict,"));
      }
    }
  }

  @Test
  void eachManagerHasAStandardCellOnTheNewestVersion() throws IOException {
    final List<String> rows = rows();

    for (final String manager : MANAGERS) {
      assertThat(rows)
          .as("standard cell on %s for %s", NEWEST, manager)
          .anyMatch(row -> row.startsWith(NEWEST + "," + manager + ",standard,"));
    }
  }

  @Test
  void everyRowNamesAFixtureThatExists() throws IOException {
    for (final String row : rows()) {
      final String fixture = row.split(",", -1)[FIXTURE_COLUMN];
      assertThat(Path.of("src/test/resources/fixtures/python-matrix", fixture))
          .as("fixture for row %s", row)
          .isDirectory();
    }
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).filter(line -> !line.isBlank()).toList();
  }
}
