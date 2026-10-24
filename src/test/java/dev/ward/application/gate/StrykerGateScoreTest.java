package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.StrykerGateFixtures.FakeReport;
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
        StrykerGateFixtures.run(
            0, new FakeReport(StrykerGateFixtures.SIXTY_PERCENT), StrykerGateFixtures.MINIMUM);

    assertThat(result.name()).isEqualTo(StrykerGateFixtures.STRYKER);
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("60%");
  }

  @Test
  void scoreBelowTheMinimumFailsStatingBothValues() {
    final GateResult result =
        StrykerGateFixtures.run(
            0, new FakeReport(StrykerGateFixtures.SIXTY_PERCENT), HIGHER_MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("60%", "minimum of 80%");
    assertThat(result.stdout()).isEqualTo(StrykerGateFixtures.STDOUT);
  }

  @Test
  void fractionalScoreJustBelowTheMinimumFails() {
    assertThat(
            StrykerGateFixtures.run(0, new FakeReport(JUST_BELOW), StrykerGateFixtures.MINIMUM)
                .status())
        .isEqualTo(GateStatus.FAILED);
  }

  @Test
  void withoutMinimumOnlyTheExitCodeCountsAndTheReportIsNotRead() {
    final FakeReport report = new FakeReport(MutationCounts.of(0, ALL));

    assertThat(StrykerGateFixtures.run(0, report, null).status()).isEqualTo(GateStatus.PASSED);
    assertThat(StrykerGateFixtures.run(1, report, null).status()).isEqualTo(GateStatus.FAILED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void failingStrykerFailsAsACommandGateWithoutReadingTheReport() {
    final FakeReport report = new FakeReport(MutationCounts.of(ALL, 0));

    assertThat(StrykerGateFixtures.run(1, report, StrykerGateFixtures.MINIMUM).status())
        .isEqualTo(GateStatus.FAILED);
    assertThat(report.reads).isEmpty();
  }

  @Test
  void reportIsReadForTheRunThatJustFinished() {
    final Instant before = Instant.now();
    final FakeReport report = new FakeReport(StrykerGateFixtures.SIXTY_PERCENT);

    StrykerGateFixtures.run(0, report, StrykerGateFixtures.MINIMUM);

    assertThat(report.reads)
        .singleElement()
        .satisfies(since -> assertThat(since).isAfterOrEqualTo(before));
  }
}
