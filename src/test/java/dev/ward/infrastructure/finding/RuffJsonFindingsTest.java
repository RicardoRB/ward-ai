package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuffJsonFindingsTest {
  private static final String GATE = "ruff";
  private static final int ERROR_LINE = 6;
  private static final int ERROR_COLUMN = 1;

  @TempDir Path root;

  private final RuffJsonFindings source = new RuffJsonFindings(new ForyJsonCodec());

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.python(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  private String output() {
    return """
        [
          {"cell": null, "code": "F821", "end_location": {"column": 19, "row": 6},
           "filename": "%s", "fix": null,
           "location": {"column": 1, "row": 6}, "message": "Undefined name `reveal_type`",
           "noqa_row": 6, "url": "https://docs.astral.sh/ruff/rules/undefined-name"}
        ]
        """
        .formatted(root.resolve("pkg/typed.py"));
  }

  @Test
  void mapsRuleAndProjectRelativeLocation() {
    assertThat(extract(output()))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo(GATE);
              assertThat(finding.tool()).isEqualTo(GATE);
              assertThat(finding.ruleId()).isEqualTo("F821");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message()).isEqualTo("Undefined name `reveal_type`");
              assertThat(finding.file()).isEqualTo("pkg/typed.py");
              assertThat(finding.line()).isEqualTo(ERROR_LINE);
              assertThat(finding.column()).isEqualTo(ERROR_COLUMN);
            });
  }

  @Test
  void packageManagerLinesBeforeTheArrayAreIgnored() {
    assertThat(extract("uv: resolving\n" + output())).singleElement();
  }

  @Test
  void syntaxErrorWithoutCodeGetsTheCatchAllRule() {
    final String stdout =
        """
        [{"cell": null, "code": null, "filename": "%s",
          "location": {"column": 8, "row": 11}, "message": "Expected a statement"}]
        """
            .formatted(root.resolve("pkg/bad.py"));

    assertThat(extract(stdout))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo(GATE);
              assertThat(finding.file()).isEqualTo("pkg/bad.py");
            });
  }

  @Test
  void emptyArrayHasNoFindings() {
    assertThat(extract("[]\n")).isEmpty();
  }

  @Test
  void nonJsonOutputHasNoFindings() {
    assertThat(extract("error: Failed to spawn: `ruff`\n")).isEmpty();
  }
}
