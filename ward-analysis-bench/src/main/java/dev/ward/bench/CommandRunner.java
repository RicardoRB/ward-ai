package dev.ward.bench;

import java.nio.file.Path;
import java.util.List;

/** Runs external commands as argument vectors. Tests replace it with a fake. */
interface CommandRunner {

  /**
   * Runs the command with its output discarded and returns its exit code, timing and peak memory.
   */
  CommandResult run(List<String> command, Path directory);

  /** Runs the command and returns its standard output; fails when the command exits non-zero. */
  String capture(List<String> command, Path directory);
}
