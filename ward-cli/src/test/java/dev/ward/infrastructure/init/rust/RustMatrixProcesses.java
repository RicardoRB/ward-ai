package dev.ward.infrastructure.init.rust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Starts the Ward jar and other processes for the Rust matrix and reads the check report. */
final class RustMatrixProcesses {
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z_]+)\"");

  private RustMatrixProcesses() {}

  /** The exit code and combined output of one process. */
  record Run(int exit, String output) {}

  /** Runs the Ward jar named by {@code jar} with {@code args} in {@code project}. */
  static Run ward(
      final String jar,
      final Map<String,
      String> env,
      final Path project,
      final String... args
  ) throws IOException, InterruptedException {
    final List<String> command =
        new ArrayList<>(List.of("java", "-jar", Path.of(jar).toAbsolutePath().toString()));
    command.addAll(List.of(args));
    return exec(env, project, command);
  }

  /** Runs {@code command} in {@code dir} with {@code env} added to the environment. */
  static Run exec(final Map<String, String> env, final Path dir, final List<String> command)
      throws IOException, InterruptedException {
    final ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
    builder.environment().putAll(env);
    builder.redirectErrorStream(true);
    final Process process = builder.start();
    final String output = new String(
        process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8
    );
    return new Run(process.waitFor(), output);
  }

  /** Returns each check's status from the JSON report, by name. */
  static Map<String, String> checks(final Run report) {
    final Map<String, String> checks = new HashMap<>();
    final Matcher match = CHECK.matcher(report.output());
    while (match.find()) {
      checks.put(match.group(1), match.group(2));
    }
    return checks;
  }
}
