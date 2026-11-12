package dev.ward.infrastructure.dependency;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.process.Command;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.Project;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs a build tool for the inventory and turns every failure into an inventory error. */
public final class ToolRuns {
  /** How long a build tool may take to resolve a project. */
  public static final Duration LIMIT = Duration.ofMinutes(10);

  private static final Logger LOGGER = Logger.getLogger(ToolRuns.class.getName());
  private static final String ERROR_PREFIX = "[ERROR]";

  private ToolRuns() {}

  /**
   * Runs {@code arguments} in the project root and returns its standard output.
   *
   * @throws DependencyInventoryException if the tool cannot be started, times out or exits with an
   *     error; the message names the executable and the first line of its error output, never the
   *     argument vector or the environment
   */
  public static String run(
      final CommandExecutor executor, final Project project, final List<String> arguments) {
    final String executable = arguments.getFirst();
    LOGGER.log(Level.FINE, () -> "event=dependency-tool-run executable=" + executable);
    final CommandResult result = executor.execute(new Command(arguments, project.root()), LIMIT);
    if (result.hasExecutionError()) {
      throw new DependencyInventoryException(
          project.root().toString(),
          executable,
          "PROCESS_TIMEOUT".equals(result.executionError())
              ? "it did not finish within " + LIMIT.toMinutes() + " minutes"
              : executable
                  + " was not found or could not be started: "
                  + firstLine(result.stderr()),
          "install " + executable + " or add its wrapper to the project");
    }
    if (!result.succeeded()) {
      throw new DependencyInventoryException(
          project.root().toString(),
          executable,
          "it exited with status " + result.exitCode() + ": " + errorLine(result),
          "make `" + executable + "` resolve the dependencies, then run again");
    }
    return result.stdout();
  }

  /** Returns the first non-blank line of {@code text}, or an empty text. */
  public static String firstLine(final String text) {
    return text.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
  }

  private static String errorLine(final CommandResult result) {
    final String stderr = firstLine(result.stderr());
    if (!stderr.isEmpty()) {
      return stderr;
    }
    return result
        .stdout()
        .lines()
        .map(String::strip)
        .filter(line -> line.startsWith(ERROR_PREFIX))
        .findFirst()
        .orElse("no error output");
  }
}
