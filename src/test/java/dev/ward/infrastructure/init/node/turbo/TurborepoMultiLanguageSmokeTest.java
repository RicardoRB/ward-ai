package dev.ward.infrastructure.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the mixed Turborepo fixture through the real CLI: {@code init}, the clean {@code check},
 * then the seeded violations of each member, which must make that member's gates fail. Runs only
 * when {@code ward.turborepo.smoke.jar} names the Ward jar, and needs the Turbo, Maven and Python
 * executables named by {@code ward.turbo.binary}, {@code ward.maven.binary} and {@code
 * ward.python.binary}; each one's directory is first on the PATH of the Ward process.
 */
@EnabledIfSystemProperty(named = TurborepoMultiLanguageSmokeTest.JAR_PROPERTY, matches = ".+")
class TurborepoMultiLanguageSmokeTest {
  static final String JAR_PROPERTY = "ward.turborepo.smoke.jar";
  private static final String TURBO_PROPERTY = "ward.turbo.binary";
  private static final String MAVEN_PROPERTY = "ward.maven.binary";
  private static final String PYTHON_PROPERTY = "ward.python.binary";
  private static final Path FIXTURE =
      Path.of("src/test/resources/fixtures/turborepo-multi-language");
  private static final Path VIOLATIONS =
      Path.of("src/test/resources/turborepo-multi-language-violations");
  private static final List<String> NOT_VIOLATED =
      List.of("compile", "coverage", "mutation", "stryker", "mutmut");
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z]+)\"");

  @TempDir Path project;

  @Test
  void initChecksCleanAndCatchesEveryMembersViolations() throws IOException, InterruptedException {
    final Map<String, String> env = environment();
    Files.createDirectories(project.resolve(".git"));
    copyTree(FIXTURE, project);

    final Run init = run(env, "init", "--preset", "standard", "--integration", "none", "--yes");
    assertThat(init.exit()).as("init output:\n%s", init.output()).isZero();
    final Map<String, String> clean =
        checks(run(env, "check", "--profile", "ci", "--format", "json"));
    assertThat(clean).as("clean check").isNotEmpty();
    assertThat(clean.entrySet().stream().filter(gate -> !"PASSED".equals(gate.getValue())).toList())
        .as("gates that fail on the clean project")
        .isEmpty();

    copyTree(VIOLATIONS, project);
    final Map<String, String> violated =
        checks(run(env, "check", "--profile", "ci", "--format", "json"));
    clean.keySet().stream()
        .filter(gate -> !NOT_VIOLATED.contains(gateName(gate)))
        .forEach(
            gate ->
                assertThat(violated.get(gate))
                    .as("seeded violation for gate %s", gate)
                    .isEqualTo("FAILED"));
  }

  /** The Turbo, Maven and Python directories first on the PATH, and uv pinned to the Python. */
  private static Map<String, String> environment() {
    final List<String> dirs = new ArrayList<>();
    for (final String property : List.of(TURBO_PROPERTY, MAVEN_PROPERTY, PYTHON_PROPERTY)) {
      final String binary = System.getProperty(property);
      Assumptions.assumeTrue(binary != null, "set -D" + property + " to run the smoke test");
      dirs.add(Path.of(binary).toAbsolutePath().getParent().toString());
    }
    final Map<String, String> env = new HashMap<>();
    env.put("PATH", String.join(":", dirs) + ":" + System.getenv("PATH"));
    env.put("UV_PYTHON", System.getProperty(PYTHON_PROPERTY));
    env.put("UV_PYTHON_DOWNLOADS", "never");
    return env;
  }

  private Run run(final Map<String, String> env, final String... args)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("java", "-jar", jar()));
    command.addAll(List.of(args));
    final ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile());
    builder.environment().putAll(env);
    builder.redirectErrorStream(true);
    final Process process = builder.start();
    final String output =
        new String(
            process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    return new Run(process.waitFor(), output);
  }

  private static String jar() {
    return Path.of(System.getProperty(JAR_PROPERTY)).toAbsolutePath().toString();
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

  /**
   * Returns the gate name without its project prefix, such as {@code checkstyle} for "apps/api:
   * checkstyle".
   */
  private static String gateName(final String check) {
    return check.substring(check.lastIndexOf(' ') + 1);
  }

  private static void copyTree(final Path source, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** The exit code and output of one Ward run. */
  private record Run(int exit, String output) {}
}
