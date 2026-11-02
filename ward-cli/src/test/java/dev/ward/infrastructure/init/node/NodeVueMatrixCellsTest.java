package dev.ward.infrastructure.init.node;

import static dev.ward.infrastructure.init.node.NodeMatrixCsv.column;
import static dev.ward.infrastructure.init.node.NodeMatrixCsv.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the Vue rows of the Node compatibility matrix offline. Each version from issue #115 must
 * appear in some cell, and Vitest 4.0.18 is only paired with Vite 7, which Ward reports as
 * incompatible with Vite 8.
 */
class NodeVueMatrixCellsTest {
  private static final int NODE_COLUMN = 0;
  private static final int FIXTURE_COLUMN = 1;
  private static final int LINTING_COLUMN = 2;
  private static final int PACKAGES_COLUMN = 5;

  @Test
  void vueCellsCoverEveryRequiredVersion() throws IOException {
    final List<String> vueRows =
        rows().stream().filter(row -> "vue".equals(column(row, FIXTURE_COLUMN))).toList();
    final List<String> declared =
        vueRows.stream()
            .flatMap(row -> Arrays.stream(column(row, PACKAGES_COLUMN).trim().split("\\s+")))
            .toList();

    assertThat(declared)
        .contains(
            "vue-tsc@3.3.12",
            "vue-tsc@3.3.13",
            "typescript@5.9.3",
            "typescript@6.0.3",
            "vitest@4.1.11",
            "vitest@4.0.18",
            "vite@7.3.7");
    assertThat(vueRows.stream().map(row -> column(row, NODE_COLUMN))).contains("22.12", "24");
    assertThat(vueRows)
        .allSatisfy(row -> assertThat(column(row, LINTING_COLUMN)).isEqualTo("eslint"));
    assertThat(vueRows)
        .filteredOn(row -> column(row, PACKAGES_COLUMN).contains("vitest@4.0.18"))
        .allSatisfy(row -> assertThat(column(row, PACKAGES_COLUMN)).contains("vite@7."));
  }
}
