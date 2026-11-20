package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportWriteException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Generic process-backed gate used by built-in and repository-defined gate registrations. */
public final class CommandQualityGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(CommandQualityGate.class.getName());
  /** The options that name a report file: trivy writes {@code --report-path}, cargo llvm-cov {@code --output-path}. */
  private static final List<String> REPORT_PATH_OPTIONS = List.of("--report-path", "--output-path");

  private final String name;
  private final CommandExecutor executor;
  private final List<String> command;
  private final ReportDirectories reportDirectories;

  /** Creates a gate that runs {@code command} without collecting report files. */
  public CommandQualityGate(
      final String name,
      final CommandExecutor executor,
      final List<String> command
  ) {
    this(name, executor, command, ReportDirectories.NONE);
  }

  /**
   * Creates a gate that runs {@code command} and keeps the reports it writes under {@code
   * reportDirectories}.
   */
  public CommandQualityGate(
      final String name,
      final CommandExecutor executor,
      final List<String> command,
      final ReportDirectories reportDirectories
  ) {
    this.name = name;
    this.executor = executor;
    this.command = List.copyOf(command);
    this.reportDirectories = reportDirectories;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    prepareReportDirectory(project);
    return GateResult.from(name, command, executor.execute(command, project.root()));
  }

  /** Lets tools such as trivy write reports into build directories that do not exist yet. */
  private void prepareReportDirectory(final Project project) {
    final int option =
        REPORT_PATH_OPTIONS.stream().mapToInt(command::indexOf).filter(
            at -> at >= 0
        ).findFirst().orElse(-1);
    if (option < 0 || option + 1 >= command.size()) {
      return;
    }
    try {
      reportDirectories.prepareParent(project.root(), command.get(option + 1));
    } catch (ReportWriteException exception) {
      LOGGER.log(
          Level.WARNING,
          () ->
              "event=report-directory-failed gate="
                  + name
                  + " error="
                  + exception.getClass().getSimpleName()
      );
    }
  }
}
