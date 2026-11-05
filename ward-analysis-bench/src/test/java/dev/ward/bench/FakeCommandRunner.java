package dev.ward.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Records every command and answers from canned results, so tests never start a real process. */
final class FakeCommandRunner implements CommandRunner {

  private static final long ELAPSED_NANOS = 2_000_000L;
  private static final long RSS_KB = 1_024L;

  final List<List<String>> commands = new ArrayList<>();
  String head = "";
  int fetchExitCode;
  boolean timeoutEveryRun;
  int outputExitCode;
  String wardOutput = "";

  @Override
  public CommandResult run(final List<String> command, final Path directory) {
    commands.add(List.copyOf(command));
    if (command.size() >= 2 && "git".equals(command.get(0)) && "checkout".equals(command.get(1))) {
      writeSource(directory);
    }
    final int exit =
        "git".equals(command.get(0)) && "fetch".equals(command.get(1)) ? fetchExitCode : 0;
    return new CommandResult(exit, ELAPSED_NANOS, RSS_KB, timeoutEveryRun);
  }

  @Override
  public String capture(final List<String> command, Path directory) {
    commands.add(List.copyOf(command));
    return head + "\n";
  }

  @Override
  public CommandOutput output(final List<String> command, final Path directory) {
    commands.add(List.copyOf(command));
    return new CommandOutput(outputExitCode, wardOutput);
  }

  private static void writeSource(final Path directory) {
    try {
      final Path source = directory.resolve("src/Main.java");
      Files.createDirectories(source.getParent());
      Files.writeString(source, "class Main {}\n");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
