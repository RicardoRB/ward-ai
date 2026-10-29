package dev.ward.application.gate;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.ToolchainSnapshot;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Projects, executors, and inspections shared by the gate prerequisite tests. */
final class GatePrerequisiteFixtures {
  static final Project MAVEN =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  static final Project GRADLE =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.GRADLE, Framework.NONE);
  static final CommandExecutor MISSING =
      (command, directory) ->
          new CommandResult(
              -1, "", "", Duration.ZERO, "PROCESS_START_FAILURE: could not start tool");

  private GatePrerequisiteFixtures() {}

  /** Reports {@code declared} as the release and {@code runtime} as the JDK, counting calls. */
  static CompatibilityInspection jdk(
      final Integer declared, final Integer runtime, final AtomicInteger calls) {
    return project -> {
      calls.incrementAndGet();
      return new CompatibilityProjectSnapshot(
          project,
          new ToolchainSnapshot(
              Optional.ofNullable(runtime),
              Optional.ofNullable(declared),
              Optional.empty(),
              Framework.NONE),
          Map.of());
    };
  }

  /** An inspection that cannot read the project. */
  static CompatibilityInspection failing() {
    return project -> {
      throw new WardException("no pom");
    };
  }

  static QualityGateRunner runner(
      final CompatibilityInspection maven, final CompatibilityInspection gradle) {
    return new QualityGateRunner(FindingExtractor.NONE, new GatePrerequisites(maven, gradle));
  }

  static GateResult only(final CheckReport report) {
    return report.results().getFirst();
  }

  static QualityGate gate(final String name, final CommandExecutor executor) {
    return new CommandQualityGate(name, executor, List.of(name, "run"));
  }

  /** A gate named {@code compile} that runs {@code build test} with {@code executor}. */
  static QualityGate buildGate(final String build, final CommandExecutor executor) {
    return new CommandQualityGate("compile", executor, List.of(build, "test"));
  }

  static CommandExecutor failingBuild() {
    return new FakeCommandExecutor(1, "", "");
  }
}
