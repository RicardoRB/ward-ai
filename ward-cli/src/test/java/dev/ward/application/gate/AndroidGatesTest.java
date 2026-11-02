package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Android gates run AGP's debug variant tasks through the Gradle wrapper. */
class AndroidGatesTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo/android"), Language.JAVA, BuildTool.GRADLE, Framework.ANDROID);
  private static final List<String> LINT = List.of("./gradlew", "lintDebug");
  private static final List<String> UNIT_TEST = List.of("./gradlew", "testDebugUnitTest");
  private static final List<String> COVERAGE =
      List.of("./gradlew", "createDebugUnitTestCoverageReport");
  private static final int MINIMUM = 80;
  private static final int COVERED = 90;
  private static final int EXECUTABLE = 100;

  @Test
  void lintGateRunsTheDebugLintTaskThroughTheWrapper() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result = run(executor, SupportedQualityGates.ANDROID_LINT, LINT, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(LINT);
  }

  @Test
  void unitTestGateRunsTheDebugUnitTestTaskThroughTheWrapper() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        run(executor, SupportedQualityGates.ANDROID_UNIT_TEST, UNIT_TEST, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(UNIT_TEST);
  }

  @Test
  void coverageGateReadsTheReportAfterItsCommandPasses() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final CoverageReport report = (project, stdout) -> LineCoverage.of(COVERED, EXECUTABLE);

    final GateResult result =
        run(executor, SupportedQualityGates.ANDROID_COVERAGE, COVERAGE, MINIMUM, report);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(COVERAGE);
  }

  @Test
  void coverageGateFailsWhenTheCommandFails() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");
    final CoverageReport report = (project, stdout) -> LineCoverage.of(COVERED, EXECUTABLE);

    final GateResult result =
        run(executor, SupportedQualityGates.ANDROID_COVERAGE, COVERAGE, MINIMUM, report);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  private static GateResult run(
      final FakeCommandExecutor executor,
      final String type,
      final List<String> command,
      final Integer minimum,
      final CoverageReport report) {
    final GateReportPorts ports =
        new GateReportPorts(
            ReportDirectories.NONE,
            null,
            null,
            null,
            new PlatformGatePorts(null, null, null, report));
    final GateConfiguration check =
        new GateConfiguration(
            "check", type, true, command, Set.of("ci"), null, null, null, minimum, null);
    return new QualityGateFactory(executor, null, new TraceabilitySources(null, null), ports, null)
        .create(new WardConfiguration(1, Map.of("check", check)))
        .getFirst()
        .execute(PROJECT);
  }
}
