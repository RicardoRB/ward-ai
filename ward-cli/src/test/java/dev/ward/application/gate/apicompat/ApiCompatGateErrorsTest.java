package dev.ward.application.gate.apicompat;

import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT;
import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ApiCompatGateErrorsTest {
  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  @Test
  void includeThatMatchesNothingIsAnExecutionError() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, CLIENT);

    final GateResult result = fixtures.run(new ApiCompatOptions(null, List.of("p.apii")));

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("p.apii");
  }

  @Test
  void baselineWithoutSourcesIsAnExecutionError() {
    fixtures.working.put(CLIENT_PATH, CLIENT);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no sources").contains("v1.0.0");
  }

  @Test
  void unparseableBaselineFileNamesFileAndRef() {
    fixtures.baseline(CLIENT_PATH, ApiCompatGateFixtures.BROKEN);
    fixtures.working.put(CLIENT_PATH, CLIENT);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(CLIENT_PATH).contains("v1.0.0");
  }

  @Test
  void unparseableWorkingFileNamesTheFile() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, ApiCompatGateFixtures.BROKEN);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(CLIENT_PATH).contains("working tree");
  }

  @Test
  void missingBaselineIsAnExecutionErrorNamingTheCause() {
    final FakeRevisions noTag = new FakeRevisions().ref("HEAD", "head");
    final GateResult result =
        new ApiCompatGate("api-compat", ApiCompatOptions.DEFAULTS, fixtures.ports(noTag))
            .execute(ApiCompatGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no release tag").contains("'base'");
  }

  @Test
  void logsCountsAndKindsButNeverSourceText() {
    final String secret = "SecretIdentifierXyz";
    fixtures.baseline(CLIENT_PATH, CLIENT.replace("close", secret));
    fixtures.working.put(CLIENT_PATH, ApiCompatGateFixtures.CLIENT_WITHOUT_CLOSE);
    final StringBuilder lines = new StringBuilder();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            lines.append(record.getMessage()).append('\n');
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    final Logger logger = Logger.getLogger("dev.ward.application.gate.apicompat");
    final Level previous = logger.getLevel();
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
    try {
      fixtures.run();
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(previous);
    }

    assertThat(lines.toString())
        .contains("event=api-compat-baseline")
        .contains("event=api-compat-findings")
        .contains("member-removed")
        .doesNotContain(secret);
  }
}
