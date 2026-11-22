package dev.ward.domain.process;

import java.time.Duration;
import java.util.List;

/**
 * The outcome of a finished or failed process.
 *
 * @param exitCode the exit code, or -1 when it could not run
 * @param stdout the captured standard output
 * @param stderr the captured standard error
 * @param duration how long it ran
 * @param executionError why it could not run, or {@code null}
 */
public record CommandResult(
    int exitCode,
    String stdout,
    String stderr,
    Duration duration,
    String executionError
) {
  /** Creates the result of a process that ran. */
  public CommandResult(
      final int exitCode,
      final String stdout,
      final String stderr,
      final Duration duration
  ) {
    this(exitCode, stdout, stderr, duration, null);
  }

  /** Returns whether the process exited with 0. */
  public boolean succeeded() {
    return exitCode == 0;
  }

  /** Returns whether the process could not be run or completed. */
  public boolean hasExecutionError() {
    return executionError != null && !executionError.isBlank();
  }

  /** Returns a newline and the last {@code count} lines of stderr, or "" when it is empty. */
  public String stderrTail(final int count) {
    final List<String> lines = stderr == null ? List.of() : stderr.strip().lines().toList();
    if (lines.isEmpty()) {
      return "";
    }
    return "\n" + String.join("\n", lines.subList(Math.max(0, lines.size() - count), lines.size()));
  }
}
