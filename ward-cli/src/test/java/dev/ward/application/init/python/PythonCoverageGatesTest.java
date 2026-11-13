package dev.ward.application.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The coverage check holds the minimums and the report path, and its command no threshold. */
class PythonCoverageGatesTest {
  private static final String COVERAGE = "coverage";

  @Test
  void coverageCommandCarriesNoThresholdWhoeverOwnsTheConfig() {
    for (final boolean owned : new boolean[] {true, false}) {
      assertThat(command(gates(BuildTool.UV, QualityPreset.STANDARD, owned), COVERAGE))
          .noneMatch(argument -> argument.contains("fail"));
    }
  }

  @Test
  void coverageCheckHoldsTheMinimumsAndTheReportPath() {
    assertThat(settings(gates(BuildTool.UV, QualityPreset.STANDARD, false), COVERAGE))
        .containsEntry("minimum", "80")
        .containsEntry("report", ".ward/reports/coverage.lcov")
        .doesNotContainKey("minimum-branches");
    assertThat(settings(gates(BuildTool.UV, QualityPreset.STRICT, false), COVERAGE))
        .containsEntry("minimum", "85")
        .containsEntry("minimum-branches", "75");
  }

  private static List<InitGateOption> gates(
      final BuildTool manager, final QualityPreset preset, final boolean owned) {
    return new PythonGates()
        .analysisGates(
            new PythonTools(manager, true, preset, Framework.NONE, owned, owned, Optional.empty()));
  }

  private static List<String> command(final List<InitGateOption> gates, final String id) {
    return option(gates, id).command();
  }

  private static Map<String, String> settings(final List<InitGateOption> gates, final String id) {
    return option(gates, id).settings();
  }

  private static InitGateOption option(final List<InitGateOption> gates, final String id) {
    return gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow();
  }
}
