package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TscOutputFindingsTest {
  private static final String GATE = "tsc";
  private static final int ERROR_LINE = 3;
  private static final int ERROR_COLUMN = 7;

  @TempDir Path root;

  private final TscOutputFindings source = new TscOutputFindings();

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.typescript(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  @Test
  void mapsAnErrorToItsDiagnosticCodeAndLocation() {
    final List<Finding> findings =
        extract(
            "src/app.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.\n");

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo(GATE);
              assertThat(finding.tool()).isEqualTo(GATE);
              assertThat(finding.ruleId()).isEqualTo("TS2322");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message())
                  .isEqualTo("Type 'string' is not assignable to type 'number'.");
              assertThat(finding.file()).isEqualTo("src/app.ts");
              assertThat(finding.line()).isEqualTo(ERROR_LINE);
              assertThat(finding.column()).isEqualTo(ERROR_COLUMN);
            });
  }

  @Test
  void mapsAWarningAndMakesAbsolutePathsProjectRelative() {
    final List<Finding> findings =
        extract(root.resolve("src/old.ts") + "(10,1): warning TS6385: 'f' is deprecated.\n");

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
              assertThat(finding.file()).isEqualTo("src/old.ts");
            });
  }

  @Test
  void continuationLinesAreNotSeparateFindings() {
    final String stdout =
        """
        src/app.ts(5,3): error TS2741: Property 'id' is missing in type '{}' but required in type 'User'.
          src/user.ts(2,3): 'id' is declared here.
        src/app.ts(5,3): error TS2741: Property 'id' is missing in type '{}' but required in type 'User'.
        """;

    assertThat(extract(stdout)).singleElement().extracting(Finding::ruleId).isEqualTo("TS2741");
  }

  @Test
  void prettyOutputGivesNoFindings() {
    final String pretty =
        "src/app.ts:3:7 - error TS2322: Type 'string' is not assignable to type 'number'.\n";

    assertThat(extract(pretty)).isEmpty();
  }
}
