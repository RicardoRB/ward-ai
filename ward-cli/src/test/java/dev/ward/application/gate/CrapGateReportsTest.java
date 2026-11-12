package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineHits;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Reports that cannot be used are never a pass. */
class CrapGateReportsTest {

  private static final int FIVE = CrapGateFixture.FIVE;
  private static final int NINE = CrapGateFixture.NINE;
  private static final String REPORT = CrapGateFixture.REPORT;
  private static final String CART = CrapGateFixture.CART;
  private static final Instant BEFORE = CrapGateFixture.BEFORE;
  private static final Instant AFTER = CrapGateFixture.AFTER;

  private final CrapGateFixture fixture = new CrapGateFixture();

  @Test
  void aMissingConventionalReportIsAnExecutionErrorNamingIt() {
    fixture.source(CART, CrapGateFixture.method(2), BEFORE);

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(REPORT, "coverage check must run first");
  }

  @Test
  void aMissingConfiguredReportIsAnExecutionErrorNamingIt() {
    fixture.source(CART, CrapGateFixture.method(2), BEFORE);

    final GateResult result = fixture.run(new CrapOptions(List.of("coverage/lcov.info"), 30));

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("coverage/lcov.info");
  }

  @Test
  void anUnparseableReportIsAnExecutionErrorAndNoScoreIsUsed() {
    fixture.source(CART, CrapGateFixture.method(NINE), BEFORE);
    fixture.unreadable(REPORT);

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("jacoco.xml", "cannot be parsed");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void aReportThatNamesNoProjectSourceFileIsAnExecutionError() {
    fixture.source(CART, CrapGateFixture.method(2), BEFORE);
    fixture.report(
        REPORT,
        AFTER,
        new LineHits.Entry(
            LineHits.Kind.PACKAGE_FILE,
            "org/other/Thing.java",
            new FileLines(new TreeSet<>(Set.of(1)), new TreeSet<>())));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("name no project source file");
  }

  @Test
  void aReportOlderThanASourceItNamesIsAnExecutionError() {
    fixture.source(CART, CrapGateFixture.method(2), AFTER);
    fixture.report(REPORT, BEFORE, fixture.entry(2, FIVE, CrapGateFixture.range(2, FIVE)));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(REPORT, CART, "older");
  }

  @Test
  void aNamedFileThatDoesNotParseIsAnExecutionErrorButAnUnnamedOneIsLeftToTheWardGate() {
    fixture.source(CART, "class Cart { int total( {{{ \n", BEFORE);
    fixture.source("src/main/java/com/shop/Broken.java", "class Broken {{{ \n", BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(1, 1, Set.of()));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(CART).doesNotContain("Broken");
  }
}
