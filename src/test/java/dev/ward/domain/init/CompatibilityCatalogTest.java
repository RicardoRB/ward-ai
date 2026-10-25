package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Checks both compatibility catalogs offline: one version per tool for each JDK, and every version
 * selected by at least one Java matrix cell, so every pinned version runs in CI.
 */
class CompatibilityCatalogTest {
  private static final int JDK_8 = 8;
  private static final int JDK_11 = 11;
  private static final int JDK_12 = 12;
  private static final int JDK_16 = 16;
  private static final int JDK_17 = 17;
  private static final int JDK_25 = 25;
  private static final Path CELLS = Path.of("src/test/resources/java-matrix/cells.csv");

  /** The tools of gates the matrix does not run, because they need network credentials. */
  private static final Set<String> NOT_IN_MATRIX =
      Set.of(
          "sonar-maven-plugin",
          "dependency-check-maven",
          "license-maven-plugin",
          "japicmp-maven-plugin",
          "org.sonarqube",
          "org.owasp.dependencycheck",
          "com.github.jk1.dependency-license-report",
          "me.champeau.gradle.japicmp");

  @Test
  void eachToolHasDisjointJdkRanges() {
    assertThat(overlaps(ManagedPluginCatalog.entries())).isEmpty();
    assertThat(overlaps(GradleToolCatalog.entries())).isEmpty();
  }

  @Test
  void overlappingRangesAreRejectedNamingTheToolAndBothVersions() {
    final Map<String, List<ManagedPluginCandidate>> overlapping =
        Map.of(
            "spotbugs",
            List.of(
                new ManagedPluginCandidate("spotbugs", "4.8.6", JDK_8, JDK_17, "7.1", "test"),
                new ManagedPluginCandidate("spotbugs", "4.9.7", JDK_11, JDK_25, "7.1", "test")));

    assertThat(overlaps(overlapping))
        .containsExactly("spotbugs 4.8.6 and 4.9.7 both run on JDK 11");
  }

  @Test
  void everyMavenVersionIsSelectedByAMatrixCell() throws IOException {
    assertThat(unused(ManagedPluginCatalog.entries(), matrixJdks("maven"))).isEmpty();
  }

  @Test
  void everyGradleVersionIsSelectedByAMatrixCell() throws IOException {
    assertThat(unused(GradleToolCatalog.entries(), matrixJdks("gradle"))).isEmpty();
  }

  @Test
  void unusedCatalogVersionIsNamed() {
    final Map<String, List<ManagedPluginCandidate>> catalog =
        Map.of(
            "pmd",
            List.of(new ManagedPluginCandidate("pmd", "7.17.0", JDK_12, JDK_16, "8.6", "test")));

    assertThat(unused(catalog, Set.of(JDK_8, JDK_11, JDK_17))).containsExactly("pmd 7.17.0");
  }

  /** One message per pair of rows of one tool whose JDK ranges share a JDK. */
  private static List<String> overlaps(final Map<String, List<ManagedPluginCandidate>> catalog) {
    final List<String> overlaps = new ArrayList<>();
    catalog.forEach(
        (tool, rows) -> {
          for (int left = 0; left < rows.size(); left++) {
            for (int right = left + 1; right < rows.size(); right++) {
              final ManagedPluginCandidate first = rows.get(left);
              final ManagedPluginCandidate second = rows.get(right);
              final int shared = Math.max(first.minimumJdk(), second.minimumJdk());
              if (shared <= Math.min(first.maximumJdk(), second.maximumJdk())) {
                overlaps.add(
                    tool
                        + " "
                        + first.version()
                        + " and "
                        + second.version()
                        + " both run on JDK "
                        + shared);
              }
            }
          }
        });
    return overlaps;
  }

  /** The {@code tool version} of every matrix tool row whose range holds no matrix JDK. */
  private static List<String> unused(
      final Map<String, List<ManagedPluginCandidate>> catalog, final Set<Integer> jdks) {
    return catalog.values().stream()
        .flatMap(List::stream)
        .filter(row -> !NOT_IN_MATRIX.contains(row.tool()))
        .filter(row -> jdks.stream().noneMatch(row::runsOn))
        .map(row -> row.tool() + " " + row.version())
        .toList();
  }

  private static Set<Integer> matrixJdks(final String buildTool) throws IOException {
    return Files.readAllLines(CELLS).stream()
        .skip(1)
        .map(row -> row.split(","))
        .filter(columns -> columns.length > 1 && buildTool.equals(columns[1]))
        .map(columns -> Integer.parseInt(columns[0]))
        .collect(Collectors.toSet());
  }
}
