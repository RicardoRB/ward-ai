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
  private static final Path NODE_FIXTURES = Path.of("src/test/resources/fixtures/node-matrix");
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

    assertThat(plainRowsEndingWith(",strict,")).containsExactlyInAnyOrderElementsOf(expected);
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

    assertThat(
            rows().stream()
                .filter(row -> row.contains(",typescript,") && !row.endsWith(","))
                .toList())
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void frameworkCellsRunStrictOnNode24WithEslint() throws IOException {
    assertThat(rows().stream().filter(row -> row.startsWith("24,") && row.contains(",strict,")))
        .as("framework rows")
        .containsAll(
            List.of(
                "24,nextjs,eslint,strict,9.22.0",
                "24,vite-react,eslint,strict,9.22.0",
                "24,nestjs,eslint,strict,",
                "24,express,eslint,strict,",
                "24,angular-21,eslint,strict,",
                "24,angular-22,eslint,strict,"));
  }

  @Test
  void standardCellsAreTypeScriptOnNode22WithEachLinter() throws IOException {
    assertThat(rowsEndingWith(",standard,"))
        .containsExactlyInAnyOrder(
            "22,typescript,eslint,standard,",
            "22,typescript,biome,standard,",
            "22,angular-21,eslint,standard,",
            "22,angular-22,eslint,standard,");
  }

  @Test
  void everySupportedAngularMajorHasItsFixtureAndBothMatrixCells() throws IOException {
    final List<String> expected =
        AngularReleases.majors().stream()
            .flatMap(
                major ->
                    List.of(
                        "22,angular-" + major + ",eslint,standard,",
                        "24,angular-" + major + ",eslint,strict,")
                        .stream())
            .toList();

    assertThat(rows().stream().filter(row -> row.split(",", -1)[1].startsWith("angular-")).toList())
        .containsExactlyInAnyOrderElementsOf(expected);
    for (final String major : AngularReleases.majors()) {
      final Path fixture = NODE_FIXTURES.resolve("angular-" + major);
      assertThat(fixture).isDirectory();
      assertThat(Files.readString(fixture.resolve("package.json")))
          .contains("\"@angular/core\": \"^" + major + ".");
    }
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).toList();
  }

  /** The rows whose last columns match, so a pinned row ends with its empty version column. */
  private static List<String> rowsEndingWith(final String suffix) throws IOException {
    return rows().stream().filter(row -> row.endsWith(suffix)).toList();
  }

  /** The rows of the plain TypeScript and JavaScript fixtures, which the matrix's sets cover. */
  private static List<String> plainRowsEndingWith(final String suffix) throws IOException {
    return rowsEndingWith(suffix).stream()
        .filter(row -> FIXTURES.contains(row.split(",", -1)[1]))
        .toList();
  }
}
