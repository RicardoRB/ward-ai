package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.config.XcodePlatform;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.Simulators.Simulator;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Runs xcodebuild test on the check's platform with a fresh result bundle. */
class XcodeTestGateTest {
  private static final Path ROOT = Path.of("/repo/shop");
  private static final Project PROJECT =
      new Project(ROOT, Language.SWIFT, BuildTool.XCODE, Framework.NONE);
  private static final List<String> COMMAND = List.of("xcodebuild", "test", "-scheme", "Shop");
  private static final List<String> BUNDLE =
      List.of("-resultBundlePath", ".ward/reports/xcode/tests.xcresult");
  private static final String DEVICE = "84D0A34E-0000-0000-0000-000000000002";
  private static final int TEST_FAILURE = 65;

  private final List<String> cleared = new ArrayList<>();
  private final List<Path> booted = new ArrayList<>();
  private Simulator simulator = Simulator.ready(DEVICE);

  private final ReportDirectories directories =
      new ReportDirectories() {
        @Override
        public void prepareParent(final Path projectRoot, final String reportPath) {
          // The prepareFresh below records the call.
        }

        @Override
        public void prepareFresh(final Path projectRoot, final String reportPath) {
          cleared.add(reportPath);
        }
      };

  private GateResult run(
      final FakeCommandExecutor executor,
      final List<String> command,
      final XcodePlatform platform) {
    final GateReportPorts ports =
        new GateReportPorts(
            directories,
            null,
            null,
            null,
            new SwiftGatePorts(
                root -> {
                  booted.add(root);
                  return simulator;
                },
                null,
                null));
    final GateConfiguration check =
        new GateConfiguration(
            "xcode",
            "xcodebuild-test",
            true,
            command,
            Set.of("ci"),
            null,
            null,
            null,
            null,
            platform);
    return new QualityGateFactory(executor, null, new TraceabilitySources(null, null), ports, null)
        .create(new WardConfiguration(1, Map.of("xcode", check)))
        .getFirst()
        .execute(PROJECT);
  }

  private static List<String> concat(final List<String> first, final List<String> second) {
    return Stream.concat(first.stream(), second.stream()).toList();
  }

  @Test
  void iosSimulatorRunsOnTheBootedDevice() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result = run(executor, COMMAND, XcodePlatform.IOS_SIMULATOR);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands)
        .containsExactly(concat(concat(COMMAND, List.of("-destination", "id=" + DEVICE)), BUNDLE));
    assertThat(executor.workingDirectories).containsExactly(ROOT);
    assertThat(booted).containsExactly(ROOT);
    assertThat(cleared).containsExactly(".ward/reports/xcode/tests.xcresult");
  }

  @Test
  void macOsRunsOnTheMacWithoutASimulator() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    run(executor, COMMAND, XcodePlatform.MACOS);

    assertThat(executor.commands)
        .containsExactly(
            concat(concat(COMMAND, List.of("-destination", "platform=macOS")), BUNDLE));
    assertThat(booted).isEmpty();
  }

  @Test
  void explicitDestinationRunsUnchangedApartFromTheResultBundle() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final List<String> command =
        concat(COMMAND, List.of("-destination", "platform=iOS Simulator,name=iPhone 17 Pro"));

    run(executor, command, null);

    assertThat(executor.commands).containsExactly(concat(command, BUNDLE));
    assertThat(booted).isEmpty();
  }

  @Test
  void failingTestsFailTheGate() {
    final GateResult result =
        run(
            new FakeCommandExecutor(TEST_FAILURE, "", "** TEST FAILED **"),
            COMMAND,
            XcodePlatform.IOS_SIMULATOR);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.exitCode()).isEqualTo(TEST_FAILURE);
  }

  @Test
  void noSimulatorIsAnExecutionErrorAndRunsNothing() {
    simulator = Simulator.unavailable("No iOS simulator runtime is installed.");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result = run(executor, COMMAND, XcodePlatform.IOS_SIMULATOR);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).isEqualTo("No iOS simulator runtime is installed.");
    assertThat(executor.commands).isEmpty();
    assertThat(cleared).isEmpty();
  }

  @Test
  void missingPlatformWithoutDestinationIsAConfigurationError() {
    assertThatThrownBy(() -> run(new FakeCommandExecutor(0, "", ""), COMMAND, null))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("xcode", "iOS Simulator", "macOS");
  }
}
