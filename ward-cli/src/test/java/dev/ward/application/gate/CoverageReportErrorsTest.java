package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An unusable or stale coverage report is an execution error, and nothing of it is logged. */
class CoverageReportErrorsTest {
  private static final int HUNDRED = 100;
  private static final int SEVENTY = 70;
  private static final int SEVENTY_FIVE = 75;
  private static final int TEN = 10;
  private static final String FILE = "com/shop/A.java";

  private CoverageGateFixture fixture;

  @BeforeEach
  void fresh() {
    fixture = new CoverageGateFixture();
  }

  @Test
  void noReportIsAnExecutionErrorThatNamesTheTestGate() {
    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("Run the test gate first");
  }

  @Test
  void anUnparseableReportIsAnExecutionErrorNamingTheFile() {
    fixture.unreadable(CoverageGateFixture.REPORT);

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("jacoco.xml cannot be parsed");
  }

  @Test
  void aReportWithNoCoverableLineIsAnExecutionError() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, 0, 0, 0, 0));

    final GateResult result = fixture.run(0, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no coverable line");
  }

  @Test
  void aBranchMinimumWithoutBranchDataIsAnExecutionError() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        false,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("did not measure branches");
  }

  @Test
  void zeroBranchesInABranchReportPassTheBranchMinimum() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("Branch coverage 0% meets the minimum of 75%");
  }

  @Test
  void aReportLeftByAnEarlierRunIsNotReadAfterACommand() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.OLD,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 0);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("left from an earlier run");
  }

  @Test
  void aSourceNewerThanTheReportMakesItStaleWithoutACommand() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.OLD,
        true,
        CoverageGateFixture.entry("Cart.java", HUNDRED, HUNDRED, 0, 0));
    fixture.source("Cart.java", CoverageGateFixture.NEW);

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("is older than Cart.java").contains("test gate");
  }

  @Test
  void logsNothingFromTheReportContent() {
    final List<String> messages = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            messages.add(record.getLevel() + " " + record.getMessage());
          }

          @Override
          public void flush() {
            // Nothing is buffered.
          }

          @Override
          public void close() {
            // Nothing is held open.
          }
        };
    final Logger logger = Logger.getLogger(CoverageReportGate.class.getName());
    logger.addHandler(handler);
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry("com/secret/Hidden.java", TEN, TEN, 0, 0));
    try {
      fixture.run(SEVENTY, null, null);
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(messages)
        .contains(Level.INFO + " event=coverage-totals gate=coverage lines=10/10 branches=0/0")
        .noneMatch(message -> message.contains("secret") || message.contains("Hidden"));
  }
}
