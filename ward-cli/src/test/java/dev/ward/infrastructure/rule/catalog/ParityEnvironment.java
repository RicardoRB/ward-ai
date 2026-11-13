package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where the tools are and where a parity run works. {@code tools} holds one folder per tool ({@code
 * checkstyle}, {@code pmd}, {@code spotbugs}, {@code errorprone}) with a {@code cp.txt} naming its
 * class path; see {@code parity/resolve-tools.sh}.
 *
 * @param tools the folder of the resolved tools
 * @param work the folder the run writes to
 * @param fixtures the folder of the Java fixtures
 */
record ParityEnvironment(Path tools, Path work, Path fixtures) {

  static final String TOOLS_PROPERTY = "ward.parity.tools";
  private static final String VIOLATING = "violating";
  private static final String PASSING = "passing";
  private static final int PATH_PARTS = 2;

  /** The environment the system properties name, empty when the tools are not set. */
  static Optional<ParityEnvironment> fromProperties() {
    final String tools = System.getProperty(TOOLS_PROPERTY, "");
    if (tools.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new ParityEnvironment(
            Path.of(tools).toAbsolutePath(),
            Path.of("target", "parity").toAbsolutePath(),
            RuleFixtureSet.root().resolve("java")));
  }

  /** Returns the class path of {@code tool}. */
  String classpath(final String tool) throws IOException {
    return Files.readString(tools.resolve(tool).resolve("cp.txt"), StandardCharsets.UTF_8).trim();
  }

  /** Returns the {@code java} launcher of the running JVM. */
  static String java() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  /** Returns the {@code javac} compiler of the running JVM. */
  static String javac() {
    return Path.of(System.getProperty("java.home"), "bin", "javac").toString();
  }

  /** Returns a work folder named {@code name}, emptied of earlier runs. */
  Path workFolder(final String name) throws IOException {
    final Path folder = work.resolve(name);
    if (Files.exists(folder)) {
      try (Stream<Path> paths = Files.walk(folder)) {
        for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
    return Files.createDirectories(folder);
  }

  /**
   * Runs {@code command} with its output written to {@code stdout} and its errors to {@code
   * stderr}, and returns its exit code.
   */
  static int run(final List<String> command, final Path stdout, final Path stderr)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(command)
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    return process.waitFor();
  }

  /** Returns the fixture a file belongs to, such as {@code my-rule/violating}, if it is one. */
  Optional<String> fixtureOf(final Path file) {
    final Path relative = fixtures.relativize(file.toAbsolutePath().normalize());
    if (relative.getNameCount() != PATH_PARTS) {
      return Optional.empty();
    }
    final String name = relative.getFileName().toString();
    final String stem = name.substring(0, name.lastIndexOf('.'));
    return VIOLATING.equals(stem) || PASSING.equals(stem)
        ? Optional.of(relative.getName(0) + "/" + stem)
        : Optional.empty();
  }
}
