package dev.ward.application.gate;

import static dev.ward.application.gate.CoverageGateFixture.NEW;
import static dev.ward.application.gate.CoverageGateFixture.OLD;
import static dev.ward.application.gate.CoverageGateFixture.REPORT;
import static dev.ward.application.gate.CoverageGateFixture.entry;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.CoverageOptions;
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

/** The {@code coverage} gate compares the line and branch totals of its reports with ward.toml. */
class CoverageReportGateTest {
  private static final int HUNDRED = 100;
  private static final int SEVENTY = 70;
  private static final int EIGHTY = 80;
  private static final int EIGHTY_FIVE = 85;
  private static final int SEVENTY_FIVE = 75;
  private static final int SIXTY = 60;
  private static final int NINETY = 90;
  private static final int THIRTY = 30;
  private static final int FORTY = 40;
  private static final int TEN = 10;
  private static final String FILE = "com/shop/A.java";

  private CoverageGateFixture fixture;

  @BeforeEach
  void fresh() {
    fixture = new CoverageGateFixture();
  }

  @Test
  void linesAtTheMinimumPass() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, SEVENTY, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).isEqualTo("Line coverage 70% meets the minimum of 70%.");
  }

  @Test
  void linesBelowTheMinimumFail() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, EIGHTY, 0, 0));

    final GateResult result = fixture.run(EIGHTY_FIVE, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("Line coverage 80% is below the minimum of 85%.");
  }

  @Test
  void branchesBelowTheirMinimumFailEvenWhenLinesPass() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, NINETY, HUNDRED, SIXTY));

    final GateResult result = fixture.run(EIGHTY_FIVE, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary())
        .contains("Line coverage 90% meets the minimum of 85%.")
        .contains("Branch coverage 60% is below the minimum of 75%.");
  }

  @Test
  void withoutABranchMinimumBranchesAreNotChecked() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, NINETY, HUNDRED, TEN));

    assertThat(fixture.run(SEVENTY, null, null).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aCommandThatFailsFailsTheGateAndReadsNoReport() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 1);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isNull();
  }

  @Test
  void aCommandThatPassesIsFollowedByTheReportRead() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 0);

    assertThat(fixture.commandRan()).isTrue();
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aCheckWithoutACommandStartsNoProcessAndReadsTheReport() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(fixture.commandRan()).isFalse();
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void moduleReportsAreSummedIntoOneTotal() {
    fixture.module("api");
    fixture.module("domain");
    fixture.report("api/" + REPORT, NEW, true, entry(FILE, FORTY, THIRTY, 0, 0));
    fixture.report("domain/" + REPORT, NEW, true, entry(FILE, TEN, TEN, 0, 0));

    final GateResult result = fixture.run(EIGHTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).isEqualTo("Line coverage 80% meets the minimum of 80%.");
  }

  @Test
  void aLineThatAnyReportCoversIsCovered() {
    fixture.report(REPORT, NEW, true, entry(FILE, 2, 0, 0, 0));
    fixture.report("second.xml", NEW, true, entry(FILE, 2, 1, 0, 0));

    final GateResult result =
        fixture.runWith(50, new CoverageOptions(List.of(REPORT, "second.xml"), null), null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("50%");
  }

  @Test
  void noReportIsAnExecutionErrorThatNamesTheTestGate() {
    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("Run the test gate first");
  }

  @Test
  void anUnparseableReportIsAnExecutionErrorNamingTheFile() {
    fixture.unreadable(REPORT);

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("jacoco.xml cannot be parsed");
  }

  @Test
  void aReportWithNoCoverableLineIsAnExecutionError() {
    fixture.report(REPORT, NEW, true, entry(FILE, 0, 0, 0, 0));

    final GateResult result = fixture.run(0, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no coverable line");
  }

  @Test
  void aBranchMinimumWithoutBranchDataIsAnExecutionError() {
    fixture.report(REPORT, NEW, false, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("did not measure branches");
  }

  @Test
  void zeroBranchesInABranchReportPassTheBranchMinimum() {
    fixture.report(REPORT, NEW, true, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("Branch coverage 0% meets the minimum of 75%");
  }

  @Test
  void aReportLeftByAnEarlierRunIsNotReadAfterACommand() {
    fixture.report(REPORT, OLD, true, entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 0);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("left from an earlier run");
  }

  @Test
  void aSourceNewerThanTheReportMakesItStaleWithoutACommand() {
    fixture.report(REPORT, OLD, true, entry("Cart.java", HUNDRED, HUNDRED, 0, 0));
    fixture.source("Cart.java", NEW);

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
    fixture.report(REPORT, NEW, true, entry("com/secret/Hidden.java", TEN, TEN, 0, 0));
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
