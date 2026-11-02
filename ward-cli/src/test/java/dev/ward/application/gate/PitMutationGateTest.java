package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.MutationGateFixtures.FakeReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MutationCounts;
import org.junit.jupiter.api.Test;

/** The PIT mutation gate checks its minimum against PIT's XML report. */
class PitMutationGateTest {
  private static final int HIGHER_MINIMUM = 80;

  private static GateResult run(
      final int exitCode, final FakeReport report, final Integer minimum) {
    return MutationGateFixtures.run(MutationGateFixtures.PIT, exitCode, report, minimum);
  }

  @Test
  void scoreAtTheMinimumPasses() {
    final GateResult result =
        run(0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), MutationGateFixtures.MINIMUM);

    assertThat(result.name()).isEqualTo(MutationGateFixtures.PIT);
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void scoreBelowTheMinimumFailsStatingBothValues() {
    final GateResult result =
        run(0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), HIGHER_MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("60%", "minimum of 80%");
  }

  @Test
  void withoutMinimumTheReportIsNotRead() {
    final FakeReport report = new FakeReport(MutationGateFixtures.SIXTY_PERCENT);

    assertThat(run(0, report, null).status()).isEqualTo(GateStatus.PASSED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void unusableReportExplainsHowToEnableXmlOutput() {
    final GateResult result =
        run(
            0,
            new FakeReport(
                MutationCounts.unusable(
                    "PIT's XML report target/pit-reports/mutations.xml is missing.")),
            MutationGateFixtures.MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("mutations.xml", "-DoutputFormats=XML,HTML");
  }
}
