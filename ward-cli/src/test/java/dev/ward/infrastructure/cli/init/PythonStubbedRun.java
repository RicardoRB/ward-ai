package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs Ward as a child JVM with the offline {@code uv} stub first on the PATH, so {@code init} and
 * {@code check} run the real gates without network access or Python tools.
 */
final class PythonStubbedRun {
  private static final Path STUBS = Path.of("src/test/resources/fixtures/python-project-bin");
  private static final long TIMEOUT_SECONDS = 180;

  private PythonStubbedRun() {}

  /** Runs {@code ward} with {@code args} in {@code project}, returning its exit code and output. */
  static Result ward(final Path project, final String... args)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElse("java"));
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add("dev.ward.WardApplication");
    command.addAll(List.of(args));
    command.add("-C");
    command.add(project.toString());
    final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(false);
    builder
        .environment()
        .put(
            "PATH",
            STUBS.toAbsolutePath() + File.pathSeparator + System.getenv().getOrDefault("PATH", ""));
    final Process process = builder.start();
    final String stdout =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final String stderr =
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), stdout, stderr);
  }

  /** One Ward run: its exit code, standard output and standard error. */
  record Result(int exit, String stdout, String stderr) {}
}
