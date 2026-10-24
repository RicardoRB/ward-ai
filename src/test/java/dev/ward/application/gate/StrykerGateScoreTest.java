package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.MutationGateFixtures.FakeReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MutationCounts;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The stryker gate passes or fails on the mutation score against its minimum. */
class StrykerGateScoreTest {
  private static final int HIGHER_MINIMUM = 80;
  private static final int ALL = 10;
  // 599 of 1000 is 59.9%, which must not round up to a pass at 60.
  private static final MutationCounts JUST_BELOW = MutationCounts.of(599, 401);

  @Test
  void scoreAtTheMinimumPasses() {
    final GateResult result =
        MutationGateFixtures.run(
            0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), MutationGateFixtures.MINIMUM);

    assertThat(result.name()).isEqualTo(MutationGateFixtures.STRYKER);
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("60%");
  }

  @Test
  void scoreBelowTheMinimumFailsStatingBothValues() {
    final GateResult result =
        MutationGateFixtures.run(
            0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), HIGHER_MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("60%", "minimum of 80%");
    assertThat(result.stdout()).isEqualTo(MutationGateFixtures.STDOUT);
  }

  @Test
  void fractionalScoreJustBelowTheMinimumFails() {
    assertThat(
            MutationGateFixtures.run(0, new FakeReport(JUST_BELOW), MutationGateFixtures.MINIMUM)
                .status())
        .isEqualTo(GateStatus.FAILED);
  }

  @Test
  void withoutMinimumOnlyTheExitCodeCountsAndTheReportIsNotRead() {
    final FakeReport report = new FakeReport(MutationCounts.of(0, ALL));

    assertThat(MutationGateFixtures.run(0, report, null).status()).isEqualTo(GateStatus.PASSED);
    assertThat(MutationGateFixtures.run(1, report, null).status()).isEqualTo(GateStatus.FAILED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void failingStrykerFailsAsACommandGateWithoutReadingTheReport() {
    final FakeReport report = new FakeReport(MutationCounts.of(ALL, 0));

    assertThat(MutationGateFixtures.run(1, report, MutationGateFixtures.MINIMUM).status())
        .isEqualTo(GateStatus.FAILED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void reportIsReadForTheRunThatJustFinished() {
    final Instant before = Instant.now();
    final FakeReport report = new FakeReport(MutationGateFixtures.SIXTY_PERCENT);

    MutationGateFixtures.run(0, report, MutationGateFixtures.MINIMUM);

    assertThat(report.reads)
        .singleElement()
        .satisfies(since -> assertThat(since).isAfterOrEqualTo(before));
  }
}
