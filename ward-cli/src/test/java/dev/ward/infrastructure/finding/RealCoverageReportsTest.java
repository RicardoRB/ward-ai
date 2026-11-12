package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.LineHits;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reads reports that real tools wrote: JaCoCo through its Maven and Gradle plugins, Vitest with the
 * lcov reporter and pytest-cov with {@code --cov-report=lcov}.
 */
class RealCoverageReportsTest {

  private static final Path DIRECTORY = Path.of("src", "test", "resources", "coverage");

  private final DetectedLineCoverage reader =
      new DetectedLineCoverage(List.of(new JacocoXmlLineCoverage(), new LcovLineCoverage()));

  @Test
  void readsTheJaCoCoReportOfTheMavenAndGradlePlugins() {
    for (final String name : List.of("jacoco-maven.xml", "jacoco-gradle.xml")) {
      final LineHits hits = reader.read(DIRECTORY.resolve(name));

      assertThat(hits.entries())
          .extracting(LineHits.Entry::name)
          .containsExactly("com/shop/Cart.java");
      assertThat(hits.entries().get(0).lines().coverable()).hasToString("[3, 5, 6, 8, 12]");
      assertThat(hits.entries().get(0).lines().covered()).hasToString("[3, 5, 6]");
    }
  }

  @Test
  void readsTheLcovOfVitestAndPytestCov() {
    final LineHits vitest = reader.read(DIRECTORY.resolve("lcov-vitest.info"));
    final LineHits pytest = reader.read(DIRECTORY.resolve("coverage-pytest.lcov"));

    assertThat(vitest.entries().get(0).name()).isEqualTo("src/cart.js");
    assertThat(vitest.entries().get(0).lines().covered()).hasToString("[2, 3]");
    assertThat(pytest.entries().get(0).name()).isEqualTo("cart.py");
    assertThat(pytest.entries().get(0).lines().covered()).hasToString("[1, 2, 3, 7]");
    assertThat(pytest.entries().get(0).lines().coverable()).hasToString("[1, 2, 3, 4, 7, 8]");
  }
}
