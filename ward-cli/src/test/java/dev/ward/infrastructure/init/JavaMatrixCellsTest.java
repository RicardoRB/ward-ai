package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the rows of the Java compatibility matrix offline, so a dropped row cannot go unnoticed.
 */
class JavaMatrixCellsTest {
  private static final Path CELLS = Path.of("src/test/resources/java-matrix/cells.csv");
  private static final Path JDKS = Path.of("src/test/resources/java-matrix/jdks.csv");
  private static final List<String> LTS = List.of("8", "11", "17", "21", "25");

  @Test
  void strictCellsCoverEveryLts() throws IOException {
    assertThat(rowsEndingWith(",strict"))
        .containsAll(LTS.stream().map(jdk -> jdk + ",maven,,strict").toList())
        .containsAll(LTS.stream().map(jdk -> jdk + ",gradle,groovy,strict").toList());
  }

  @Test
  void kotlinDslAndStandardCells() throws IOException {
    assertThat(rows().stream().filter(row -> row.contains(",kotlin,")).toList())
        .containsExactlyInAnyOrder("8,gradle,kotlin,strict", "25,gradle,kotlin,strict");
    assertThat(rowsEndingWith(",standard"))
        .containsExactlyInAnyOrder(
            "8,maven,,standard",
            "25,maven,,standard",
            "8,gradle,groovy,standard",
            "25,gradle,groovy,standard");
  }

  @Test
  void everyMatrixJdkHasAGradleVersion() throws IOException {
    final List<String> mapped =
        Files.readAllLines(JDKS).stream().skip(1).map(row -> row.split(",")[0]).toList();

    assertThat(mapped)
        .containsAll(rows().stream().map(row -> row.split(",")[0]).distinct().toList());
  }

  /** The cell rows, without the header. */
  static List<String> rows() throws IOException {
    return Files.readAllLines(CELLS).stream().skip(1).filter(row -> !row.isBlank()).toList();
  }

  private static List<String> rowsEndingWith(final String suffix) throws IOException {
    return rows().stream().filter(row -> row.endsWith(suffix)).toList();
  }
}
