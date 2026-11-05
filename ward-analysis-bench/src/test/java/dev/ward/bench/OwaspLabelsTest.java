package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Maps OWASP Benchmark's expected results to the Ward rules of the three injection classes. */
class OwaspLabelsTest {

  private static final String ROOT = "src/main/java/org/owasp/benchmark/testcode";

  @Test
  void mapsTheThreeInjectionCategoriesAndIgnoresOthers() {
    final List<AccuracyLabel> labels =
        OwaspLabels.parse(
            List.of(
                "# test name, category, real vulnerability, cwe",
                "BenchmarkTest00001,pathtraver,true,22",
                "BenchmarkTest00002,sqli,false,89",
                "BenchmarkTest00003,cmdi,true,78",
                "BenchmarkTest00004,xss,true,79"),
            ROOT);

    assertThat(labels)
        .containsExactly(
            new AccuracyLabel(ROOT + "/BenchmarkTest00001.java", "ward:java/path-injection", true),
            new AccuracyLabel(ROOT + "/BenchmarkTest00002.java", "ward:java/sql-injection", false),
            new AccuracyLabel(
                ROOT + "/BenchmarkTest00003.java", "ward:java/command-injection", true));
  }

  @Test
  void rejectsARowWithTooFewColumns() {
    assertThatThrownBy(() -> OwaspLabels.parse(List.of("BenchmarkTest00001,sqli"), ROOT))
        .hasMessageContaining("too few columns");
  }
}
