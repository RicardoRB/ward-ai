package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each Swift matrix cell through the real CLI with that Xcode selected for the Ward process
 * only, through {@code DEVELOPER_DIR}: {@code init}, the clean {@code check}, then the seeded
 * violations, which must make every selected gate except {@code swift-coverage} fail. Under the
 * standard preset {@code swift-build} passes on the violations, because only strict treats warnings
 * as errors. Runs only for the cells whose Xcode is named by {@code ward.swift.matrix}, and for the
 * fixture named by {@code ward.swift.matrix.fixture} when set.
 */
@EnabledIfSystemProperty(
    named = SwiftCompatibilityMatrixTest.VERSION_PROPERTY,
    matches = "\\d+\\.\\d+(\\.\\d+)?")
class SwiftCompatibilityMatrixTest {
  static final String VERSION_PROPERTY = "ward.swift.matrix";
  private static final String FIXTURE_PROPERTY = "ward.swift.matrix.fixture";
  private static final String JAR_PROPERTY = "ward.swift.matrix.jar";
  private static final String DEVELOPER_DIR_PROPERTY = "ward.swift.matrix.developer-dir";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/swift-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/swift-matrix/violations");
  private static final Set<String> NOT_VIOLATED = Set.of("swift-coverage");
  private static final Set<String> STANDARD_PASSES = Set.of("swift-build");
  private static final String STANDARD = "standard";

  /**
   * The style whose layers match the fixtures' Domain and Infrastructure folders, so Harmonize's
   * Domain-to-Infrastructure rule applies to the seeded violation.
   */
  private static final String ARCHITECTURE = "clean";

  private static final int FIXTURE_COLUMN = 3;
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z]+)\"");

  @ParameterizedTest(name = "xcode {0} {1} {2} {3}")
  @CsvFileSource(resources = "/swift-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell, @TempDir final Path project)
      throws IOException, InterruptedException {
    final String xcode = cell.getString(0);
    final String preset = cell.getString(2);
    final String fixture = cell.getString(FIXTURE_COLUMN);
    Assumptions.assumeTrue(
        xcode.equals(System.getProperty(VERSION_PROPERTY)), "row is for Xcode " + xcode);
    Assumptions.assumeTrue(
        fixture.equals(System.getProperty(FIXTURE_PROPERTY, fixture)), "row is for " + fixture);
    final Map<String, String> env = environment(xcode);
    assertSelectedXcode(xcode, env, project);
    copyTree(FIXTURES.resolve(fixture), project);

    final Run init =
        run(
            env,
            project,
            "init",
            "--preset",
            preset,
            "--architecture",
            ARCHITECTURE,
            "--integration",
            "none",
            "--yes");
    assertThat(init.exit()).as("init output:\n%s", init.output()).isZero();
    final Map<String, String> clean =
        checks(run(env, project, "check", "--profile", "ci", "--format", "json"));
    assertThat(clean).as("clean check").isNotEmpty();
    assertThat(
            clean.entrySet().stream()
                .filter(gate -> !"PASSED".equals(gate.getValue()))
                .map(Map.Entry::getKey)
                .toList())
        .as("gates that fail on the clean project")
        .isEmpty();

    copyTree(VIOLATIONS.resolve(fixture), project);
    final Map<String, String> violated =
        checks(run(env, project, "check", "--profile", "ci", "--format", "json"));
    clean.keySet().stream()
        .filter(gate -> !NOT_VIOLATED.contains(gate))
        .filter(gate -> !(STANDARD.equals(preset) && STANDARD_PASSES.contains(gate)))
        .forEach(
            gate ->
                assertThat(violated.get(gate))
                    .as("seeded violation for gate %s", gate)
                    .isEqualTo("FAILED"));
  }

  /**
   * Returns the environment that selects {@code xcode} for the Ward process: {@code DEVELOPER_DIR}
   * points at that Xcode, unless {@code ward.swift.matrix.developer-dir} overrides the path.
   */
  private static Map<String, String> environment(final String xcode) {
    return Map.of(
        "DEVELOPER_DIR",
        System.getProperty(
            DEVELOPER_DIR_PROPERTY, "/Applications/Xcode_" + xcode + ".app/Contents/Developer"));
  }

  /** Fails when {@code xcodebuild -version} under {@code env} does not report {@code xcode}. */
  private static void assertSelectedXcode(
      final String xcode, final Map<String, String> env, final Path project)
      throws IOException, InterruptedException {
    final Run version = exec(env, project, List.of("xcodebuild", "-version"));
    final String reported = version.output().lines().findFirst().orElse("").strip();
    assertThat(reported)
        .as("Xcode selected for cell %s, from:\n%s", xcode, version.output())
        .isEqualTo("Xcode " + xcode);
  }

  private static Run run(final Map<String, String> env, final Path project, final String... args)
      throws IOException, InterruptedException {
    final List<String> command =
        new ArrayList<>(
            List.of(
                "java",
                "-jar",
                Path.of(System.getProperty(JAR_PROPERTY)).toAbsolutePath().toString()));
    command.addAll(List.of(args));
    return exec(env, project, command);
  }

  private static Run exec(final Map<String, String> env, final Path dir, final List<String> command)
      throws IOException, InterruptedException {
    final ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
    builder.environment().putAll(env);
    builder.redirectErrorStream(true);
    final Process process = builder.start();
    final String output =
        new String(
            process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    return new Run(process.waitFor(), output);
  }

  /** Returns each check's status from the JSON report, by name. */
  private static Map<String, String> checks(final Run report) {
    final Map<String, String> checks = new HashMap<>();
    final Matcher match = CHECK.matcher(report.output());
    while (match.find()) {
      checks.put(match.group(1), match.group(2));
    }
    return checks;
  }

  private static void copyTree(final Path source, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(file, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** The exit code and standard output of one process. */
  private record Run(int exit, String output) {}
}
