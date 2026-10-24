package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs Ward in a child JVM on a copy of an offline Node fixture, with stub {@code npm} and {@code
 * npx} first on the path; a JVM cannot change its own {@code PATH}.
 */
final class NodeFixtureRun {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final Path STUBS = FIXTURES.resolve("typescript-project-bin");
  private static final long TIMEOUT_SECONDS = 120;

  private final Path project;

  private NodeFixtureRun(final Path project) {
    this.project = project;
  }

  /** Copies the fixture named {@code fixture} into {@code project}. */
  static NodeFixtureRun copy(final String fixture, final Path project) throws IOException {
    final Path source = FIXTURES.resolve(fixture);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path target = project.resolve(source.relativize(file));
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
      }
    }
    return new NodeFixtureRun(project);
  }

  /** Returns the content of {@code file} in the project. */
  String read(final String file) throws IOException {
    return Files.readString(project.resolve(file));
  }

  /** Runs {@code ward} with {@code args} followed by {@code -C <project>}. */
  Result ward(final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElse("java"));
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add("dev.ward.WardApplication");
    command.addAll(List.of(args));
    command.add("-C");
    command.add(project.toString());
    final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder
        .environment()
        .put(
            "PATH",
            STUBS.toAbsolutePath() + File.pathSeparator + System.getenv().getOrDefault("PATH", ""));
    final Process process = builder.start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), output);
  }

  /**
   * One Ward run.
   *
   * @param exit the exit code
   * @param output stdout and stderr together
   */
  record Result(int exit, String output) {}
}
