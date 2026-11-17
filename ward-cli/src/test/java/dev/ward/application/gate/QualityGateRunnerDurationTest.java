package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QualityGateRunnerDurationTest {
  private static final long TICK_NANOS = 7_000_000L;

  private static QualityGate nativeGate(final String name) {
    return new QualityGate() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public GateResult execute(final Project project) {
        return GateResult.builder(name, GateStatus.PASSED).command(List.of()).build();
      }
    };
  }

  @Test
  @DisplayName("durations are reported")
  void durationsAreReported() {
    // Given a run of format, ward and secrets
    final Project project =
        new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final AtomicLong clock = new AtomicLong();
    final QualityGateRunner runner =
        new QualityGateRunner(
            FindingExtractor.NONE,
            new GatePrerequisites(),
            () -> clock.addAndGet(TICK_NANOS)
        );

    // When the report is produced
    final CheckReport report =
        runner.run(
            project,
            List.of(nativeGate("format"), nativeGate("ward"), nativeGate("secrets"))
        );

    // Then each check's duration is above 0 and matches its run (one tick of the fake clock)
    assertThat(report.results())
        .extracting(GateResult::duration)
        .containsOnly(Duration.ofNanos(TICK_NANOS));
  }
}
