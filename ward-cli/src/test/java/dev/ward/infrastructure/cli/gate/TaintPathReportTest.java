package dev.ward.infrastructure.cli.gate;

import static dev.ward.infrastructure.cli.gate.TaintPathFixtures.json;
import static dev.ward.infrastructure.cli.gate.TaintPathFixtures.jsonOf;
import static dev.ward.infrastructure.cli.gate.TaintPathFixtures.sarif;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs the Java taint fixture through lowering, the taint check, the finding mapper and both report
 * renderers, and compares the path output with goldens written by hand from the fixture.
 */
class TaintPathReportTest {

  private static final String GOLDEN = "src/test/resources/golden/";
  private static final int STEPS = 3;

  /** Scenario: Taint finding in JSON. The path has the three steps, in source-to-sink order. */
  @Test
  void jsonPathHoldsTheThreeStepsOfTheFixture() throws IOException {
    final JsonTree finding = JsonTree.parse(json(true)).get("checks").get(0).get("findings").get(0);
    assertThat(finding.get("path").size()).isEqualTo(STEPS);
    assertThat(compact(json(true))).contains(compact(golden("taint-path-json.txt")));
  }

  /** Scenario: Ordinary finding. A finding without a path has no path field. */
  @Test
  void findingWithoutPathHasNoPathField() {
    final Finding plain =
        new Finding(
            "checkstyle",
            "checkstyle",
            "Rule",
            FindingSeverity.ERROR,
            "message",
            TaintPathFixtures.FILE,
            1,
            1,
            null,
            List.of());
    final JsonTree check = JsonTree.parse(jsonOf(plain)).get("checks").get(0);
    assertThat(check.get("findings").get(0).has("path")).isFalse();
  }

  /** Scenario: Taint result in SARIF. Its code flow lists the three steps in order. */
  @Test
  void sarifCodeFlowListsTheThreeStepsOfTheFixture() throws IOException {
    assertThat(compact(sarif(true))).contains(compact(golden("taint-path-sarif.txt")));
  }

  /** Scenario: Results without a path have no code flow. */
  @Test
  void sarifResultWithoutPathHasNoCodeFlow() {
    assertThat(sarif(false)).doesNotContain("codeFlows");
  }

  private static String golden(final String name) throws IOException {
    return Files.readString(Path.of(GOLDEN + name), StandardCharsets.UTF_8);
  }

  private static String compact(final String text) {
    return text.replaceAll("\\s", "");
  }
}
