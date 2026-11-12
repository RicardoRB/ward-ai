package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Scoring and logging of the {@code crap} gate. */
class CrapGateTest {

  private static final int SIX = CrapGateFixture.SIX;
  private static final int NINE = CrapGateFixture.NINE;
  private static final int SIX_END = CrapGateFixture.SIX_END;
  private static final int SIX_SCORE = CrapGateFixture.SIX_SCORE;
  private static final String REPORT = CrapGateFixture.REPORT;
  private static final String CART = CrapGateFixture.CART;
  private static final Instant BEFORE = CrapGateFixture.BEFORE;
  private static final Instant AFTER = CrapGateFixture.AFTER;

  private final CrapGateFixture fixture = new CrapGateFixture();

  @Test
  void anUntestedComplexMethodFailsWithOneFindingAtItsLocation() {
    fixture.source(CART, CrapGateFixture.method(SIX), BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(2, SIX_END, Set.of()));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("crap");
              assertThat(finding.file()).isEqualTo(CART);
              assertThat(finding.line()).isEqualTo(2);
              assertThat(finding.message()).contains("42", "30", "complexity 6", "coverage 0%");
            });
    assertThat(result.summary()).contains("1 function(s) scored", "1 above 30");
  }

  @Test
  void aTestedMethodPassesAndTheSummaryCountsFunctions() {
    fixture.source(CART, CrapGateFixture.method(SIX), BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(2, SIX_END, CrapGateFixture.range(2, SIX_END)));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("1 function(s) scored", "0 above");
  }

  @Test
  void aCustomLimitIsUsed() {
    fixture.source(CART, CrapGateFixture.method(SIX), BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(2, SIX_END, Set.of()));

    assertThat(fixture.run(new CrapOptions(List.of(), SIX_SCORE)).status())
        .isEqualTo(GateStatus.PASSED);
    assertThat(fixture.run(new CrapOptions(List.of(), SIX_SCORE - 1)).status())
        .isEqualTo(GateStatus.FAILED);
  }

  @Test
  void aFileTheReportsDoNotNameIsScoredFromComplexityAlone() {
    fixture.source(CART, CrapGateFixture.method(SIX), BEFORE);
    fixture.source(
        "src/main/java/com/shop/Other.java",
        CrapGateFixture.method(NINE).replace("Cart", "Other"),
        BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(2, SIX_END, CrapGateFixture.range(2, SIX_END)));

    final GateResult result = fixture.run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("2 function(s) scored");
  }

  @Test
  void logsTheReportsReadAndTheFunctionsScored() {
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
    final Logger reports = Logger.getLogger(CrapReports.class.getName());
    final Logger gate = Logger.getLogger(CrapGate.class.getName());
    reports.addHandler(handler);
    gate.addHandler(handler);
    fixture.source(CART, CrapGateFixture.method(SIX), BEFORE);
    fixture.report(REPORT, AFTER, fixture.entry(2, SIX_END, Set.of()));
    try {
      fixture.run();
    } finally {
      reports.removeHandler(handler);
      gate.removeHandler(handler);
    }

    assertThat(messages)
        .contains(Level.INFO + " event=crap-reports gate=crap reports=1")
        .anyMatch(
            message ->
                message.startsWith(
                    Level.INFO + " event=crap-scored gate=crap functions=1 above=1"));
  }
}
