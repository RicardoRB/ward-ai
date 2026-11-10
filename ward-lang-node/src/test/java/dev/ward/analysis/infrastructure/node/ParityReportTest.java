package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The committed parity report names every structural rule, so no rule ships unmeasured. */
class ParityReportTest {

  private static final List<String> METRICS =
      List.of(
          "function-length",
          "parameter-count",
          "complexity",
          "file-length",
          "naming",
          "nesting-depth");

  @Test
  void parityReportNamesEveryStructuralRule() throws IOException {
    final String report = Files.readString(Path.of("PARITY.md"));

    for (final String metric : METRICS) {
      assertThat(report).contains("`ward:typescript/" + metric + "`");
      assertThat(report).contains("`ward:javascript/" + metric + "`");
    }
  }
}
