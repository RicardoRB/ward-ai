package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateSection;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateLinesTest {
  private static final String GATE = "ward";
  private static final int ERRORS = 3;
  private static final int WARNINGS = 7;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  @Test
  void textReportListsEachSectionOfGateAndJsonKeepsOneCheck() {
    final CheckReport report =
        report(
            GateResult.builder(GATE, GateStatus.FAILED)
                .errors(ERRORS)
                .build()
                .withSections(
                    List.of(
                        new GateSection("naming", 0, 0),
                        new GateSection("style", ERRORS, 0),
                        new GateSection("docs", 0, WARNINGS))));

    assertThat(new TextReportRenderer().render(report))
        .contains("✓ ward · naming", "✗ ward · style — 3 errors", "✓ ward · docs — 7 warnings")
        .contains("0 passed · 1 failed · 0 skipped");
    final JsonTree checks =
        JsonTree.parse(new JsonReportRenderer(new ForyJsonCodec()).render(report)).get("checks");
    assertThat(checks.get(0).get("name").asString()).isEqualTo(GATE);
    assertThat(checks.get(0).has("sections")).isFalse();
  }

  @Test
  void gateThatCouldNotRunKeepsItsSingleLine() {
    final CheckReport report =
        report(
            GateResult.builder(GATE, GateStatus.EXECUTION_ERROR)
                .build()
                .withSections(List.of(new GateSection("style", 0, 0))));

    assertThat(new TextReportRenderer().render(report))
        .contains("✗ ward — error")
        .doesNotContain("ward · style");
  }

  private CheckReport report(final GateResult result) {
    return new CheckReport(project, List.of(result));
  }
}
