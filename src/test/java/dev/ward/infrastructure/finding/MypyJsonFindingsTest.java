package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MypyJsonFindingsTest {
  private static final String GATE = "mypy";
  private static final int ERROR_LINE = 5;
  private static final int ERROR_COLUMN = 3;

  @TempDir Path root;

  private static final String ARG_TYPE =
      "{\"file\": \"pkg/typed.py\", \"line\": 5, \"column\": 2, \"end_line\": 5, "
          + "\"end_column\": 5, \"message\": \"Argument 1 to \\\"f\\\" has incompatible type "
          + "\\\"str\\\"; expected \\\"int\\\"\", \"hint\": null, \"code\": \"arg-type\", "
          + "\"severity\": \"error\"}";

  private static final String NOTE =
      "{\"file\": \"pkg/typed.py\", \"line\": 6, \"column\": 12, \"end_line\": 6, "
          + "\"end_column\": 13, \"message\": \"Revealed type is \\\"def (x: int) -> str\\\"\", "
          + "\"hint\": null, \"code\": \"misc\", \"severity\": \"note\"}";

  private final MypyJsonFindings source = new MypyJsonFindings(new ForyJsonCodec());

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.python(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  @Test
  void mapsErrorCodeAndOneBasedColumn() {
    assertThat(extract(ARG_TYPE + "\n"))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo(GATE);
              assertThat(finding.tool()).isEqualTo(GATE);
              assertThat(finding.ruleId()).isEqualTo("arg-type");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message()).startsWith("Argument 1 to \"f\"");
              assertThat(finding.file()).isEqualTo("pkg/typed.py");
              assertThat(finding.line()).isEqualTo(ERROR_LINE);
              assertThat(finding.column()).isEqualTo(ERROR_COLUMN);
            });
  }

  @Test
  void notesAreSkipped() {
    assertThat(extract(ARG_TYPE + "\n" + NOTE + "\n")).singleElement();
  }

  @Test
  void interleavedToolLinesAndNonJsonLinesAreSkipped() {
    assertThat(extract("uv: resolving\nnot json at all\n" + ARG_TYPE + "\n")).singleElement();
  }

  @Test
  void diagnosticWithoutCodeGetsTheCatchAllRule() {
    final String line =
        "{\"file\": \"pkg/typed.py\", \"line\": 1, \"column\": 0, \"message\": \"Plugin says no\", "
            + "\"hint\": null, \"code\": null, \"severity\": \"error\"}";

    assertThat(extract(line))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo(GATE);
              assertThat(finding.column()).isEqualTo(1);
            });
  }

  @Test
  void nonPositiveLineHasNoLocation() {
    final String line =
        "{\"file\": \"pkg/typed.py\", \"line\": -1, \"column\": -1, \"message\": \"Config\", "
            + "\"hint\": null, \"code\": \"misc\", \"severity\": \"error\"}";

    assertThat(extract(line))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.line()).isNull();
              assertThat(finding.column()).isNull();
            });
  }
}
