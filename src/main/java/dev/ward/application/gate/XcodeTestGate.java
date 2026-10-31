package dev.ward.application.gate;

import dev.ward.domain.config.XcodePlatform;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportWriteException;
import dev.ward.domain.gate.Simulators;
import dev.ward.domain.gate.Simulators.Simulator;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs {@code xcodebuild test} on the check's platform, booting an iPhone simulator first when it
 * is the iOS Simulator, and writes a fresh result bundle for the coverage gate to read.
 */
final class XcodeTestGate implements QualityGate {
  /** Where every run writes its result bundle; {@code xcodebuild} refuses an existing one. */
  static final String RESULT_BUNDLE = ".ward/reports/xcode/tests.xcresult";

  static final String DESTINATION = "-destination";
  private static final Logger LOGGER = Logger.getLogger(XcodeTestGate.class.getName());
  private static final String RESULT_BUNDLE_OPTION = "-resultBundlePath";

  private final String name;
  private final CommandExecutor executor;
  private final List<String> command;
  private final XcodePlatform platform;
  private final Simulators simulators;
  private final ReportDirectories reportDirectories;

  /**
   * {@code platform} is ignored when {@code command} names a destination; {@code simulators} is
   * only used for the iOS Simulator.
   */
  XcodeTestGate(
      final String name,
      final CommandExecutor executor,
      final List<String> command,
      final XcodePlatform platform,
      final GateReportPorts ports) {
    this.name = name;
    this.executor = executor;
    this.command = List.copyOf(command);
    this.platform = platform;
    this.simulators = ports.swift().simulators();
    this.reportDirectories = ports.directories();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final List<String> arguments = new ArrayList<>(command);
    if (!command.contains(DESTINATION)) {
      final Simulator target = destination(project);
      if (!target.isReady()) {
        return executionError(command, target.problem());
      }
      arguments.add(DESTINATION);
      arguments.add(target.deviceId());
    }
    try {
      reportDirectories.prepareFresh(project.root(), RESULT_BUNDLE);
    } catch (ReportWriteException exception) {
      return executionError(
          arguments, "Ward could not remove the earlier result bundle " + RESULT_BUNDLE + ".");
    }
    arguments.add(RESULT_BUNDLE_OPTION);
    arguments.add(RESULT_BUNDLE);
    return GateResult.from(name, arguments, executor.execute(arguments, project.root()));
  }

  /** Returns the {@code -destination} value for the platform, or why no simulator is ready. */
  private Simulator destination(final Project project) {
    if (platform == XcodePlatform.MACOS) {
      LOGGER.log(Level.INFO, () -> "event=xcode-destination gate=" + name + " platform=macos");
      return Simulator.ready("platform=macOS");
    }
    final Simulator simulator = simulators.readyIPhone(project.root());
    LOGGER.log(
        Level.INFO,
        () ->
            "event=xcode-destination gate="
                + name
                + " platform=ios-simulator ready="
                + simulator.isReady());
    return simulator.isReady() ? Simulator.ready("id=" + simulator.deviceId()) : simulator;
  }

  private GateResult executionError(final List<String> arguments, final String summary) {
    return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
        .command(List.copyOf(arguments))
        .exitCode(-1)
        .summary(summary)
        .build();
  }
}
