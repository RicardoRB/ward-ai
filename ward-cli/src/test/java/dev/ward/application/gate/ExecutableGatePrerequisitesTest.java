package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GatePrerequisite;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ExecutableGatePrerequisitesTest {

  @Test
  void missingKnownExecutableGetsCatalogHint() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.failing(), GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.gate(
                            "gitleaks", GatePrerequisiteFixtures.MISSING))));

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.summary())
        .startsWith("Gate 'gitleaks' could not start 'gitleaks'.")
        .contains("Install gitleaks:");
    assertThat(result.prerequisite().kind()).isEqualTo(GatePrerequisite.Kind.EXECUTABLE);
    assertThat(result.prerequisite().executable()).isEqualTo("gitleaks");
    assertThat(result.prerequisite().remediation()).contains("gitleaks");
  }

  @Test
  void missingUnknownExecutableGetsPathFallback() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.failing(), GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.gate(
                            "custom-lint", GatePrerequisiteFixtures.MISSING))));

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.prerequisite().remediation())
        .isEqualTo("Install custom-lint or add it to PATH.");
    assertThat(result.summary()).contains("custom-lint");
  }

  @Test
  void nonZeroExitStaysFailedWithoutPrerequisite() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.failing(), GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.gate(
                            "checkstyle", new FakeCommandExecutor(1, "", "bad")))));

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.prerequisite()).isNull();
  }

  @Test
  void executableOnlyRunnerStillExplainsMissingTools() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            new QualityGateRunner()
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.gate(
                            "gitleaks", GatePrerequisiteFixtures.MISSING))));

    assertThat(result.prerequisite().kind()).isEqualTo(GatePrerequisite.Kind.EXECUTABLE);
  }

  @Test
  void logsPrerequisiteWithGateAndKindOnly() {
    final List<String> messages = new ArrayList<>();
    final Logger logger = Logger.getLogger(GatePrerequisites.class.getName());
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            messages.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      GatePrerequisiteFixtures.runner(
              GatePrerequisiteFixtures.failing(), GatePrerequisiteFixtures.failing())
          .run(
              GatePrerequisiteFixtures.MAVEN,
              List.of(GatePrerequisiteFixtures.gate("gitleaks", GatePrerequisiteFixtures.MISSING)));
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(messages).containsExactly("event=gate-prerequisite gate=gitleaks kind=executable");
  }
}
