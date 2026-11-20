package dev.ward.infrastructure.init.rust;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The offline check of the Rust matrix rows: the stable toolchain and the minimum supported
 * version each have a strict cell for both editions, and every row names a fixture and the files
 * its cell needs.
 */
class RustMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/rust-matrix/cells.csv");
  private static final List<String> TOOLCHAINS = List.of("stable", "1.85");
  private static final List<String> EDITIONS = List.of("2021", "2024");
  private static final int FIXTURE_COLUMN = 3;

  @Test
  @DisplayName("a strict cell for each toolchain and edition")
  void everyToolchainHasAStrictCellForBothEditions() throws IOException {
    final List<String> rows = rows();

    for (final String toolchain : TOOLCHAINS) {
      for (final String edition : EDITIONS) {
        assertThat(rows)
            .as("strict cell for %s, edition %s", toolchain, edition)
            .contains(toolchain + "," + edition + ",strict,basic");
      }
    }
  }

  @Test
  @DisplayName("a standard cell for each toolchain")
  void everyToolchainHasAStandardCell() throws IOException {
    for (final String toolchain : TOOLCHAINS) {
      assertThat(rows())
          .as("standard cell for %s", toolchain)
          .anyMatch(row -> row.startsWith(toolchain + ",") && row.contains(",standard,"));
    }
  }

  @Test
  @DisplayName("a fixture with its violations and its untested module")
  void everyRowNamesAFixtureWithItsMatrixFiles() throws IOException {
    for (final String row : rows()) {
      final String fixture = row.split(",", -1) [FIXTURE_COLUMN];
      assertThat(Path.of("src/test/resources/fixtures/rust-matrix", fixture, "Cargo.toml"))
          .as("fixture for row %s", row)
          .exists();
      assertThat(Path.of("src/test/resources/rust-matrix/violations", fixture)).isDirectory();
      assertThat(Path.of("src/test/resources/rust-matrix/coverage", fixture)).isDirectory();
    }
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).filter(line -> !line.isBlank()).toList();
  }
}
