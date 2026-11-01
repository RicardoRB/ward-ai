package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.XcodePlatform;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.Simulators.Simulator;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every Swift type is a known gate, and the plain ones run their command as configured. */
class SwiftGatesTest {
  private static final Path ROOT = Path.of("/repo/shop");
  private static final Project PROJECT =
      new Project(ROOT, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  private static final int MINIMUM = 80;
  private static final List<String> TYPES =
      List.of(
          "swift-format",
          "swiftlint",
          "swift-build",
          "swift-test",
          "xcodebuild-test",
          "swift-coverage",
          "harmonize");

  private static QualityGateFactory factory(final FakeCommandExecutor executor) {
    return new QualityGateFactory(
        executor,
        null,
        new TraceabilitySources(null, null),
        new GateReportPorts(
            ReportDirectories.NONE,
            null,
            null,
            null,
            new SwiftGatePorts(
                root -> Simulator.ready("device"),
                (project, stdout) -> LineCoverage.of(1, 1),
                (project, stdout) -> LineCoverage.of(1, 1))),
        null);
  }

  private static GateConfiguration check(final String type, final List<String> command) {
    return new GateConfiguration(
        type,
        type,
        true,
        command,
        Set.of("ci"),
        null,
        null,
        null,
        "swift-coverage".equals(type) ? MINIMUM : null,
        "xcodebuild-test".equals(type) ? XcodePlatform.MACOS : null);
  }

  @Test
  void everySwiftTypeIsAcceptedWithUses() {
    final Map<String, GateConfiguration> checks = new LinkedHashMap<>();
    TYPES.forEach(type -> checks.put(type, check(type, List.of("tool"))));

    final List<QualityGate> gates =
        factory(new FakeCommandExecutor(0, "", "")).create(new WardConfiguration(1, checks));

    assertThat(gates).extracting(QualityGate::name).containsExactlyElementsOf(TYPES);
  }

  @Test
  void swiftBuildRunsExactlyItsCommandFromTheProjectRoot() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final List<String> command = List.of("swift", "build", "--build-tests");

    final GateResult result =
        factory(executor)
            .create(new WardConfiguration(1, Map.of("build", check("swift-build", command))))
            .getFirst()
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(command);
    assertThat(executor.workingDirectories).containsExactly(ROOT);
  }

  @Test
  void harmonizeRunsTheRulesPackageAndFailsWhenItsTestsFail() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");
    final List<String> command =
        List.of("swift", "test", "--package-path", ".ward/swift-architecture");

    final GateResult result =
        factory(executor)
            .create(new WardConfiguration(1, Map.of("rules", check("harmonize", command))))
            .getFirst()
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(executor.commands).containsExactly(command);
  }
}
