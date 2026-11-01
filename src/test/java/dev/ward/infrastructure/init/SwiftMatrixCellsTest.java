package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The offline check of the Swift matrix rows: the required Xcode and build tool cells exist, every
 * cell names a fixture and its violations, and every Xcode has a runner.
 */
class SwiftMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/swift-matrix/cells.csv");
  private static final Path RUNNERS = Path.of("src/test/resources/swift-matrix/xcodes.csv");
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/swift-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/swift-matrix/violations");
  private static final List<String> REQUIRED =
      List.of(
          "26.0.1,swiftpm,standard",
          "26.0.1,xcode,standard",
          "26.6,swiftpm,strict",
          "26.6,xcode,strict");
  private static final int XCODE_COLUMN = 0;
  private static final int FIXTURE_COLUMN = 3;

  @Test
  void everyRequiredRowIsPresent() throws IOException {
    assertThat(missing(rows(), REQUIRED)).isEmpty();
  }

  @Test
  void aMissingRequiredRowIsNamed() throws IOException {
    final List<String> withoutStrictXcode =
        rows().stream().filter(row -> !row.startsWith("26.6,xcode,strict,")).toList();

    assertThat(missing(withoutStrictXcode, REQUIRED)).containsExactly("26.6,xcode,strict");
  }

  @Test
  void everyRowNamesAFixtureWithViolations() throws IOException {
    for (final String row : rows()) {
      final String fixture = row.split(",", -1)[FIXTURE_COLUMN];
      assertThat(FIXTURES.resolve(fixture)).as("fixture for row %s", row).isDirectory();
      assertThat(VIOLATIONS.resolve(fixture)).as("violations for row %s", row).isDirectory();
    }
  }

  @Test
  void everyCellXcodeHasARunner() throws IOException {
    final Set<String> runners =
        Files.readAllLines(RUNNERS).stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> line.split(",", -1)[0])
            .collect(Collectors.toSet());
    final Set<String> xcodes =
        rows().stream().map(row -> row.split(",", -1)[XCODE_COLUMN]).collect(Collectors.toSet());

    assertThat(xcodes).allSatisfy(xcode -> assertThat(runners).contains(xcode));
  }

  /** Returns each required row, as {@code xcode,buildtool,preset}, that no row starts with. */
  private static List<String> missing(final List<String> rows, final List<String> required) {
    final List<String> missing = new ArrayList<>();
    for (final String row : required) {
      if (rows.stream().noneMatch(line -> line.startsWith(row + ","))) {
        missing.add(row);
      }
    }
    return missing;
  }

  private static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).filter(line -> !line.isBlank()).toList();
  }
}
