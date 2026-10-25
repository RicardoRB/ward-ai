package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the rows of the Node compatibility matrix offline, so a dropped row cannot go unnoticed.
 */
class NodeMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/node-matrix/cells.csv");
  private static final List<String> NODES = List.of("22", "24");
  private static final List<String> FIXTURES =
      List.of("typescript", "javascript-esm", "javascript-commonjs");
  private static final List<String> LINTERS = List.of("eslint", "biome");

  @Test
  void strictCellsCoverEveryCombination() throws IOException {
    final List<String> expected =
        NODES.stream()
            .flatMap(
                node ->
                    FIXTURES.stream()
                        .flatMap(
                            fixture ->
                                LINTERS.stream()
                                    .map(
                                        linting ->
                                            node + "," + fixture + "," + linting + ",strict")))
            .toList();

    assertThat(rowsWithPreset("strict")).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void standardCellsAreTypeScriptOnNode22WithEachLinter() throws IOException {
    assertThat(rowsWithPreset("standard"))
        .containsExactlyInAnyOrder("22,typescript,eslint,standard", "22,typescript,biome,standard");
  }

  private static List<String> rowsWithPreset(final String preset) throws IOException {
    return Files.readAllLines(CELLS).stream()
        .skip(1)
        .filter(row -> row.endsWith("," + preset))
        .toList();
  }
}
