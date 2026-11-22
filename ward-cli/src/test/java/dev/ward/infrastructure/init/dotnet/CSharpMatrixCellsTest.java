package dev.ward.infrastructure.init.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The offline check of the C# matrix rows: every supported SDK has a cell and a fixture. */
class CSharpMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/csharp-matrix/cells.csv");
  private static final List<String> SDKS = List.of("8", "9", "10");
  private static final int FIXTURE_COLUMN = 2;

  @Test
  void everySupportedSdkHasAStrictCell() throws IOException {
    for (final String sdk : SDKS) {
      assertThat(rows()).as("strict cell for SDK %s", sdk).contains(sdk + ",strict,basic");
    }
  }

  @Test
  void theOldestAndTheNewestSdkHaveAStandardCell() throws IOException {
    assertThat(rows()).contains("8,standard,basic", "10,standard,basic");
  }

  @Test
  void everyRowNamesAFixtureWithItsSeededViolations() throws IOException {
    for (final String row : rows()) {
      final String fixture = row.split(",", -1) [FIXTURE_COLUMN];
      assertThat(Path.of("src/test/resources/fixtures/csharp-matrix", fixture))
          .as("fixture for row %s", row)
          .isDirectory();
      for (final String folder : List.of("violations", "coverage")) {
        assertThat(Path.of("src/test/resources/csharp-matrix", folder, fixture))
            .as("%s of row %s", folder, row)
            .isDirectory();
      }
      assertThat(Path.of("src/test/resources/csharp-matrix/violations", fixture + "-compile-error"))
          .as("compile error of row %s", row)
          .isDirectory();
    }
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).filter(line -> !line.isBlank()).toList();
  }
}
