package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

  /** The oldest and newest supported version of each linter, declared in the TypeScript rows. */
  private static final Map<String, List<String>> DECLARED_VERSIONS =
      Map.of("eslint", List.of("9.22.0", "10.13.0"), "biome", List.of("2.3.0", "2.6.3"));

  @Test
  void strictCellsCoverEveryCombinationAtThePinnedVersion() throws IOException {
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
                                            node + "," + fixture + "," + linting + ",strict,")))
            .toList();

    assertThat(rowsEndingWith(",strict,")).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void typeScriptCellsDeclareBothEndsOfEachLinterRange() throws IOException {
    final List<String> expected =
        NODES.stream()
            .flatMap(
                node ->
                    DECLARED_VERSIONS.entrySet().stream()
                        .flatMap(
                            entry ->
                                entry.getValue().stream()
                                    .map(
                                        version ->
                                            node
                                                + ",typescript,"
                                                + entry.getKey()
                                                + ",strict,"
                                                + version)))
            .toList();

    assertThat(rows().stream().filter(row -> !row.endsWith(",")).toList())
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void standardCellsAreTypeScriptOnNode22WithEachLinter() throws IOException {
    assertThat(rowsEndingWith(",standard,"))
        .containsExactlyInAnyOrder(
            "22,typescript,eslint,standard,", "22,typescript,biome,standard,");
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).toList();
  }

  /** The rows whose last columns match, so a pinned row ends with its empty version column. */
  private static List<String> rowsEndingWith(final String suffix) throws IOException {
    return rows().stream().filter(row -> row.endsWith(suffix)).toList();
  }
}
