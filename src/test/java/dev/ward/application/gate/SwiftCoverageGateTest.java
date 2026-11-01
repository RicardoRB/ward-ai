package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A swift-coverage check fails below its minimum line coverage and never passes unread. */
class SwiftCoverageGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  private static final List<String> SWIFTPM = List.of("swift", "test", "--show-codecov-path");
  private static final List<String> XCCOV =
      List.of("xcrun", "xccov", "view", "--report", "--json", ".ward/reports/xcode/tests.xcresult");
  private static final String STDOUT = "/repo/shop/.build/debug/codecov/Shop.json\n";
  private static final int MINIMUM = 80;
  private static final int ALL_LINES = 100;
  private static final int APP_COVERED = 30;
  private static final int APP_LINES = 50;
  private static final int SOURCES_COVERED = 18;
  private static final int SOURCES_LINES = 20;

  private final List<String> llvmReads = new ArrayList<>();
  private final List<String> xccovReads = new ArrayList<>();
  private LineCoverage coverage = LineCoverage.of(SOURCES_COVERED, SOURCES_LINES);

  private GateResult run(final int exitCode, final List<String> command, final Integer minimum) {
    final CoverageReport llvmCov = recording(llvmReads);
    final CoverageReport xccov = recording(xccovReads);
    final GateReportPorts ports =
        new GateReportPorts(
            ReportDirectories.NONE, null, null, null, new PlatformGatePorts(null, llvmCov, xccov));
    final GateConfiguration check =
        new GateConfiguration(
            "coverage",
            "swift-coverage",
            true,
            command,
            Set.of("ci"),
            null,
            null,
            null,
            minimum,
            null);
    return new QualityGateFactory(
            new FakeCommandExecutor(exitCode, STDOUT, ""),
            null,
            new TraceabilitySources(null, null),
            ports,
            null)
        .create(new WardConfiguration(1, Map.of("coverage", check)))
        .getFirst()
        .execute(PROJECT);
  }

  private CoverageReport recording(final List<String> reads) {
    return (project, stdout) -> {
      reads.add(stdout);
      return coverage;
    };
  }

  @Test
  void passesAboveTheMinimumAndReportsTheCoverage() {
    final GateResult result = run(0, SWIFTPM, MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("90%", "80%");
    assertThat(llvmReads).containsExactly(STDOUT);
    assertThat(xccovReads).isEmpty();
  }

  @Test
  void passesExactlyAtTheMinimum() {
    coverage = LineCoverage.of(MINIMUM, ALL_LINES);

    assertThat(run(0, SWIFTPM, MINIMUM).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void xccovCommandReadsTheXcodeReportAndFailsBelowTheMinimum() {
    coverage = LineCoverage.of(APP_COVERED, APP_LINES);

    final GateResult result = run(0, XCCOV, MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("60%", "below");
    assertThat(xccovReads).hasSize(1);
    assertThat(llvmReads).isEmpty();
  }

  @Test
  void failingCommandDoesNotReadAReport() {
    assertThat(run(1, SWIFTPM, MINIMUM).status()).isEqualTo(GateStatus.FAILED);
    assertThat(llvmReads).isEmpty();
  }

  @Test
  void unusableReportIsAnExecutionError() {
    coverage = LineCoverage.unusable("The llvm-cov export .build/x.json is missing.");

    final GateResult result = run(0, SWIFTPM, MINIMUM);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("missing");
  }

  @Test
  void missingMinimumIsAConfigurationError() {
    assertThatThrownBy(() -> run(0, SWIFTPM, null))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("coverage", "minimum");
  }
}
