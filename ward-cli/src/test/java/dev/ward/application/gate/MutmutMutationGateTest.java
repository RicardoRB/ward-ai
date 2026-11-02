package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.MutationGateFixtures.FakeReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MutationCounts;
import org.junit.jupiter.api.Test;

/** The mutmut gate checks its minimum against the per-file results mutmut 3 wrote. */
class MutmutMutationGateTest {
  private static final int HIGHER_MINIMUM = 80;

  private static GateResult run(
      final int exitCode, final FakeReport report, final Integer minimum) {
    return MutationGateFixtures.run(MutationGateFixtures.MUTMUT, exitCode, report, minimum);
  }

  @Test
  void scoreAtTheMinimumPasses() {
    final GateResult result =
        run(0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), MutationGateFixtures.MINIMUM);

    assertThat(result.name()).isEqualTo(MutationGateFixtures.MUTMUT);
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
  void withoutMinimumTheResultsAreNotRead() {
    final FakeReport report = new FakeReport(MutationGateFixtures.SIXTY_PERCENT);

    assertThat(run(0, report, null).status()).isEqualTo(GateStatus.PASSED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void failingCommandFailsWithoutReadingTheResults() {
    final FakeReport report = new FakeReport(MutationGateFixtures.SIXTY_PERCENT);

    final GateResult result = run(1, report, MutationGateFixtures.MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void unusableResultsExplainHowToRunMutmut() {
    final GateResult result =
        run(
            0,
            new FakeReport(MutationCounts.unusable("mutmut's results under mutants/ are missing.")),
            MutationGateFixtures.MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("mutants/", "`mutmut run` from mutmut 3");
  }
}
