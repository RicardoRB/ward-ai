package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The processes a Java matrix cell runs: Ward on the test JVM, and Git, with the cell's JDK as
 * {@code JAVA_HOME} and first on {@code PATH} so the build tool and every gate run on it.
 */
final class JavaMatrixProcesses {
  private static final String JAR_PROPERTY = "ward.java.matrix.jar";
  private static final String HOME_PROPERTY = "ward.java.matrix.home";

  private JavaMatrixProcesses() {}

  static CommandRun ward(final Path dir, final String... args)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElse("java"));
    command.add("-jar");
    command.add(Path.of(System.getProperty(JAR_PROPERTY)).toAbsolutePath().toString());
    command.addAll(List.of(args));
    return run(dir, command);
  }

  static void git(final Path dir, final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    assertThat(run(dir, command).exitCode()).as("git %s", String.join(" ", args)).isZero();
  }

  /** Runs {@code command} with the cell's JDK as {@code JAVA_HOME} and first on {@code PATH}. */
  static CommandRun run(final Path dir, final List<String> command)
      throws IOException, InterruptedException {
    final Path home = Path.of(System.getProperty(HOME_PROPERTY)).toAbsolutePath();
    final ProcessBuilder builder =
        new ProcessBuilder(command)
            .directory(dir.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT);
    builder.environment().put("JAVA_HOME", home.toString());
    builder
        .environment()
        .put("PATH", home.resolve("bin") + File.pathSeparator + System.getenv("PATH"));
    final Process process = builder.start();
    final String stdout =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new CommandRun(process.waitFor(), stdout);
  }

  /** Copies {@code from} into {@code to}, keeping the wrapper script executable. */
  static void copyTree(final Path from, final Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (final Path source : files.toList()) {
        final Path target = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(
              source,
              target,
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }

  /** The stdout and exit status of one process. */
  record CommandRun(int exitCode, String stdout) {}
}
