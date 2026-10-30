package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every pinned tool declares a Python range that the Python matrix cells run. */
class CompatibilityCatalogTest {
  private static final Path CELLS = Path.of("src/test/resources/python-matrix/cells.csv");
  private static final int FIRST_MINOR = 11;
  private static final int LAST_EXCLUSIVE_MINOR = 15;

  @Test
  void everyPythonCatalogRowHasAMatrixCellInItsRange() throws IOException {
    final List<String> versions =
        Files.readAllLines(CELLS).stream()
            .skip(1)
            .map(line -> line.split(",", -1)[0])
            .distinct()
            .toList();

    assertThat(PythonPackageCatalog.packages())
        .allSatisfy(
            tool ->
                assertThat(versions)
                    .as("matrix cell for %s in %s", tool.name(), tool.pythonRange())
                    .anyMatch(version -> inRange(version, tool.pythonRange())));
  }

  /**
   * Returns whether {@code version} satisfies the {@code >=3.11,<3.15} style of range the catalog
   * uses.
   */
  private static boolean inRange(final String version, final String range) {
    final String[] parts = version.split("\\.");
    final int minor = Integer.parseInt(parts[1]);
    return range.contains(">=3.11")
        && minor >= FIRST_MINOR
        && range.contains("<3.15")
        && minor < LAST_EXCLUSIVE_MINOR;
  }
}
