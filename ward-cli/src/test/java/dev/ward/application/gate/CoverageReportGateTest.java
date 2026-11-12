package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.CoverageOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
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
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, SEVENTY, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).isEqualTo("Line coverage 70% meets the minimum of 70%.");
  }

  @Test
  void linesBelowTheMinimumFail() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, EIGHTY, 0, 0));

    final GateResult result = fixture.run(EIGHTY_FIVE, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("Line coverage 80% is below the minimum of 85%.");
  }

  @Test
  void branchesBelowTheirMinimumFailEvenWhenLinesPass() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, NINETY, HUNDRED, SIXTY));

    final GateResult result = fixture.run(EIGHTY_FIVE, SEVENTY_FIVE, null);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary())
        .contains("Line coverage 90% meets the minimum of 85%.")
        .contains("Branch coverage 60% is below the minimum of 75%.");
  }

  @Test
  void withoutABranchMinimumBranchesAreNotChecked() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, NINETY, HUNDRED, TEN));

    assertThat(fixture.run(SEVENTY, null, null).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aCommandThatFailsFailsTheGateAndReadsNoReport() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 1);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isNull();
  }

  @Test
  void aCommandThatPassesIsFollowedByTheReportRead() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, 0);

    assertThat(fixture.commandRan()).isTrue();
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aCheckWithoutACommandStartsNoProcessAndReadsTheReport() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, HUNDRED, HUNDRED, 0, 0));

    final GateResult result = fixture.run(SEVENTY, null, null);

    assertThat(fixture.commandRan()).isFalse();
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void moduleReportsAreSummedIntoOneTotal() {
    fixture.module("api");
    fixture.module("domain");
    fixture.report(
        "api/" + CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, FORTY, THIRTY, 0, 0));
    fixture.report(
        "domain/" + CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, TEN, TEN, 0, 0));

    final GateResult result = fixture.run(EIGHTY, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).isEqualTo("Line coverage 80% meets the minimum of 80%.");
  }

  @Test
  void aLineThatAnyReportCoversIsCovered() {
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        CoverageGateFixture.entry(FILE, 2, 0, 0, 0));
    fixture.report(
        "second.xml", CoverageGateFixture.NEW, true, CoverageGateFixture.entry(FILE, 2, 1, 0, 0));

    final GateResult result =
        fixture.runWith(
            50, new CoverageOptions(List.of(CoverageGateFixture.REPORT, "second.xml"), null), null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("50%");
  }
}
