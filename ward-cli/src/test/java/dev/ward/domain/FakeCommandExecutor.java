package dev.ward.domain;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Records each command and returns the configured results in order, repeating the last one. */
public class FakeCommandExecutor implements CommandExecutor {
  /** The duration every fake command reports. */
  public static final Duration DURATION = Duration.ofMillis(1234);

  public final List<List<String>> commands = new ArrayList<>();
  public final List<Path> workingDirectories = new ArrayList<>();
  private final Deque<CommandResult> results;

  /**
   * Creates an executor that answers every command with {@code exitCode}, {@code stdout}, and
   * {@code stderr}.
   */
  public FakeCommandExecutor(final int exitCode, final String stdout, final String stderr) {
    this(new CommandResult(exitCode, stdout, stderr, DURATION));
  }

  /** Creates an executor that answers the first command with the first result, and so on. */
  public FakeCommandExecutor(final CommandResult... results) {
    this.results = new ArrayDeque<>(List.of(results));
  }

  @Override
  public CommandResult execute(final List<String> command, final Path workingDirectory) {
    commands.add(command);
    workingDirectories.add(workingDirectory);
    return results.size() > 1 ? results.poll() : results.peek();
  }
}
