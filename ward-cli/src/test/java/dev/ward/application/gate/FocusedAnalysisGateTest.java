package dev.ward.application.gate;

import static dev.ward.application.gate.FocusedGateFixtures.LAYER;
import static dev.ward.application.gate.FocusedGateFixtures.LENGTH;
import static dev.ward.application.gate.FocusedGateFixtures.UNITS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A narrowed {@code ward} gate reports the findings that touch its changed files only. */
class FocusedAnalysisGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.SPRING_BOOT);

  @Test
  void structuralFindingInAnUnchangedFileIsNotReported() {
    final GateResult result = FocusedGateFixtures.gate(List.of("src/A.java")).execute(PROJECT);

    assertThat(files(result, LENGTH)).containsExactly("src/A.java");
  }

  @Test
  void dependentsLayerViolationIsReportedAndUnrelatedFileIsNot() {
    final GateResult result = FocusedGateFixtures.gate(List.of("src/A.java")).execute(PROJECT);

    assertThat(files(result, LAYER)).containsExactlyInAnyOrder("src/A.java", "src/W.java");
  }

  @Test
  void emptyFocusReportsTheWholeProject() {
    final GateResult result = FocusedGateFixtures.gate(List.of()).execute(PROJECT);

    assertThat(files(result, LENGTH)).containsExactlyInAnyOrderElementsOf(UNITS);
    assertThat(files(result, LAYER)).containsExactlyInAnyOrderElementsOf(UNITS);
  }

  private static List<String> files(final GateResult result, final String ruleId) {
    return result.findings().items().stream()
        .filter(finding -> ruleId.equals(finding.ruleId()))
        .map(Finding::file)
        .toList();
  }
}
