package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.gate.MutationGateFixtures.FakeReport;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MutationCounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** An unusable Stryker report is never a pass, and the gate logs what it decided. */
class StrykerGateReportTest {
  private static final String REPORTER = "json reporter";

  @Test
  void unusableReportIsAnExecutionErrorNamingTheProblemAndTheReporter() {
    final GateResult result =
        MutationGateFixtures.run(
            0,
            new FakeReport(
                MutationCounts.unusable(
                    "Stryker's JSON report reports/mutation/mutation.json is missing.")),
            MutationGateFixtures.MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("is missing", REPORTER);
  }

  @Test
  void reportWithoutValidMutantsIsAnExecutionError() {
    final GateResult result =
        MutationGateFixtures.run(
            0, new FakeReport(MutationCounts.of(0, 0)), MutationGateFixtures.MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no mutation score can be computed", REPORTER);
  }

  @Test
  void minimumWithoutAMutationReportPortIsAConfigurationError() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(
            () ->
                factory.create(
                    new WardConfiguration(
                        1,
                        Map.of(
                            MutationGateFixtures.STRYKER,
                            MutationGateFixtures.gate(MutationGateFixtures.MINIMUM)))))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'stryker' gate");
  }

  @Test
  void logsTheScoreAndUnusableReportsWithoutCapturedOutput() {
    final List<LogRecord> records = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            records.add(logRecord);
          }

          @Override
          public void flush() {
            // Records are kept in memory.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    final Logger logger = Logger.getLogger(MutationScoreGate.class.getName());
    logger.addHandler(handler);
    try {
      MutationGateFixtures.run(
          0, new FakeReport(MutationGateFixtures.SIXTY_PERCENT), MutationGateFixtures.MINIMUM);
      MutationGateFixtures.run(
          0, new FakeReport(MutationCounts.unusable("missing")), MutationGateFixtures.MINIMUM);
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(records)
        .extracting(LogRecord::getMessage)
        .anySatisfy(
            message ->
                assertThat(message).contains("event=mutation-score gate=stryker", "score=60"))
        .anySatisfy(
            message -> assertThat(message).contains("event=mutation-report-unusable gate=stryker"))
        .allSatisfy(message -> assertThat(message).doesNotContain("stdout-marker"));
  }
}
