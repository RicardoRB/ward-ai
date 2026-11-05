package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs commands on {@link ProcessBuilder} and samples the process tree's resident memory. */
final class ProcessCommandRunner implements CommandRunner {

  private static final long SAMPLE_INTERVAL_MILLIS = 50;
  private static final Duration TIMEOUT = Duration.ofMinutes(30);
  private static final List<String> PROCESS_TABLE = List.of("ps", "-A", "-o", "pid=,ppid=,rss=");

  @Override
  public CommandResult run(final List<String> command, final Path directory) {
    final Process process = start(command, directory, ProcessBuilder.Redirect.DISCARD);
    final long started = System.nanoTime();
    long peakKb = treeRssKb(process.pid());
    boolean timedOut = false;
    try {
      while (!process.waitFor(SAMPLE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
        peakKb = Math.max(peakKb, treeRssKb(process.pid()));
        if (System.nanoTime() - started > TIMEOUT.toNanos()) {
          process.destroyForcibly();
          timedOut = true;
          break;
        }
      }
      final int exitCode = process.waitFor();
      final long elapsed = System.nanoTime() - started;
      return new CommandResult(exitCode, elapsed, peakKb, timedOut);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IllegalStateException("Benchmark command was interrupted", e);
    }
  }

  @Override
  public String capture(final List<String> command, final Path directory) {
    final Process process = start(command, directory, ProcessBuilder.Redirect.PIPE);
    try {
      final byte[] output = process.getInputStream().readAllBytes();
      final int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException("Command " + command.get(0) + " exited with " + exitCode);
      }
      return new String(output, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read output of " + command.get(0), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Command was interrupted", e);
    }
  }

  private static long treeRssKb(final long pid) {
    return ProcessTreeRss.sum(pid, processTable());
  }

  private static String processTable() {
    final Process ps = start(PROCESS_TABLE, null, ProcessBuilder.Redirect.PIPE);
    try {
      final byte[] table = ps.getInputStream().readAllBytes();
      ps.waitFor();
      return new String(table, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the process table", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Process table read was interrupted", e);
    }
  }

  private static Process start(
      final List<String> command, final Path directory, final ProcessBuilder.Redirect stdout) {
    final List<String> vector = new ArrayList<>(command);
    if (directory != null && vector.get(0).startsWith("./")) {
      vector.set(0, directory.resolve(vector.get(0).substring(2)).toString());
    }
    final ProcessBuilder builder =
        new ProcessBuilder(vector)
            .redirectOutput(stdout)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    if (directory != null) {
      builder.directory(directory.toFile());
    }
    try {
      return builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start " + command.get(0), e);
    }
  }
}
