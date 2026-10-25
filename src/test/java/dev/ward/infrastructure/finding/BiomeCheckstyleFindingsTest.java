package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BiomeCheckstyleFindingsTest {
  private static final String GATE = "biome";
  private static final int ANY_COLUMN = 10;
  // Captured from `biome ci --colors=off --reporter=checkstyle --error-on-warnings .` in Biome
  // 2.5.15.
  private static final String CAPTURED =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <checkstyle version="4.3">
        <file name="biome.jsonc">
          <error line="4" column="12" severity="info" message="The use of the recommended field has been deprecated." source="deserialize" />
        </file>
        <file name="src/x.ts">
          <error line="1" column="10" severity="error" message="Unexpected any. Specify a different type." source="lint/suspicious/noExplicitAny" />
          <error line="0" column="0" severity="error" message="File content differs from formatting output" source="format" />
        </file>
      </checkstyle>
      """;

  @TempDir Path root;

  private final BiomeCheckstyleFindings source = new BiomeCheckstyleFindings();

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.typescript(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  @Test
  void mapsErrorsWithRuleAndProjectRelativeLocation() {
    final List<Finding> findings = extract(CAPTURED);

    assertThat(findings).hasSize(2);
    final Finding any = findings.getFirst();
    assertThat(any.gateId()).isEqualTo(GATE);
    assertThat(any.tool()).isEqualTo(GATE);
    assertThat(any.ruleId()).isEqualTo("lint/suspicious/noExplicitAny");
    assertThat(any.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(any.message()).isEqualTo("Unexpected any. Specify a different type.");
    assertThat(any.file()).isEqualTo("src/x.ts");
    assertThat(any.line()).isEqualTo(1);
    assertThat(any.column()).isEqualTo(ANY_COLUMN);
  }

  @Test
  void dropsNonPositiveLocations() {
    final Finding format = extract(CAPTURED).get(1);

    assertThat(format.ruleId()).isEqualTo("format");
    assertThat(format.line()).isNull();
    assertThat(format.column()).isNull();
  }

  @Test
  void mapsWarningsToWarningSeverity() {
    final String stdout =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <checkstyle version="4.3">
          <file name="src/y.ts">
            <error line="3" column="2" severity="warning" message="Suspicious." source="lint/suspicious/noConsole" />
          </file>
        </checkstyle>
        """;

    assertThat(extract(stdout))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
              assertThat(finding.ruleId()).isEqualTo("lint/suspicious/noConsole");
            });
  }

  @Test
  void skipsInfoDiagnostics() {
    assertThat(extract(CAPTURED)).noneMatch(finding -> "deserialize".equals(finding.ruleId()));
  }

  @Test
  void readsXmlAfterPackageManagerPreamble() {
    final String stdout = "yarn run v1.22.22\n$ biome ci\n" + CAPTURED;

    assertThat(extract(stdout)).hasSize(2);
  }

  @Test
  void unparsableOutputGivesNoFindings() {
    assertThat(extract("<?xml version=\"1.0\"?><checkstyle><file name=\"a.ts\">")).isEmpty();
    assertThat(extract("Checked 3 files in 2ms. No fixes applied.")).isEmpty();
  }

  @Test
  void missingStdoutGivesNoFindings() {
    assertThat(
            source.extract(
                FindingFixtures.typescript(root),
                FindingFixtures.failedWithStderr(GATE, "boom"),
                FindingFixtures.justBefore()))
        .isEmpty();
  }
}
