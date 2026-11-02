package dev.ward.infrastructure.init.python;

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
 * Runs each Python matrix cell through the real CLI: {@code init}, the clean {@code check}, then
 * the seeded violations, which must make every selected gate except {@code coverage} and {@code
 * mutmut} fail. Each cell runs under its Python interpreter, which is first on the PATH of the Ward
 * process only, so the test process is unchanged. Runs only for the cells whose Python version is
 * named by {@code ward.python.matrix}, and for the fixture named by {@code
 * ward.python.matrix.fixture} when set.
 */
@EnabledIfSystemProperty(
    named = PythonCompatibilityMatrixTest.VERSION_PROPERTY,
    matches = "\\d+\\.\\d+")
class PythonCompatibilityMatrixTest {
  static final String VERSION_PROPERTY = "ward.python.matrix";
  private static final String FIXTURE_PROPERTY = "ward.python.matrix.fixture";
  private static final String JAR_PROPERTY = "ward.python.matrix.jar";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/python-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/python-matrix/violations");
  private static final Set<String> NOT_VIOLATED = Set.of("coverage", "mutmut");
  private static final String PIP = "pip";
  private static final int PACKAGES_COLUMN = 4;
  private static final String POETRY = "poetry";
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z]+)\"");

  @ParameterizedTest(name = "python {0} {1} {2} {3}")
  @CsvFileSource(resources = "/python-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell, @TempDir final Path project, @TempDir final Path bin)
      throws IOException, InterruptedException {
    final String python = cell.getString(0);
    final String manager = cell.getString(1);
    final String preset = cell.getString(2);
    final String fixture = cell.getString(3);
    Assumptions.assumeTrue(
        python.equals(System.getProperty(VERSION_PROPERTY)), "row is for Python " + python);
    Assumptions.assumeTrue(
        fixture.equals(System.getProperty(FIXTURE_PROPERTY, fixture)), "row is for " + fixture);
    final Map<String, String> env = environment(python, bin);
    copyTree(FIXTURES.resolve(fixture), project);
    Files.writeString(project.resolve(".python-version"), python + "\n");
    pinPackages(project, cell.size() > PACKAGES_COLUMN ? cell.getString(PACKAGES_COLUMN) : "");
    prepareManager(manager, find(python), project, env);

    final Run init =
        run(env, project, "init", "--preset", preset, "--integration", "none", "--yes");
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
        .forEach(
            gate ->
                assertThat(violated.get(gate))
                    .as("seeded violation for gate %s", gate)
                    .isEqualTo("FAILED"));
  }

  /**
   * Pins each {@code name==version} of the row's packages column in the fixture's pyproject,
   * replacing the quoted requirement of the same name, so a row can test one release of a
   * framework.
   */
  private static void pinPackages(final Path project, final String packages) throws IOException {
    if (packages == null || packages.isBlank()) {
      return;
    }
    final Path pyproject = project.resolve("pyproject.toml");
    String text = Files.readString(pyproject);
    for (final String requirement : packages.split(" ")) {
      final String name = requirement.split("[=<>~!]", 2)[0];
      final Matcher pinned =
          Pattern.compile("\"" + Pattern.quote(name) + "[^\"]*\"", Pattern.CASE_INSENSITIVE)
              .matcher(text);
      assertThat(pinned.find()).as("fixture declares %s", name).isTrue();
      text = pinned.replaceFirst(Matcher.quoteReplacement("\"" + requirement + "\""));
    }
    Files.writeString(pyproject, text);
  }

  /**
   * Puts {@code python} first for the Ward process, and makes uv use it without downloading one.
   */
  private static Map<String, String> environment(final String python, final Path bin)
      throws IOException, InterruptedException {
    final Path interpreter = Path.of(find(python));
    Files.createSymbolicLink(bin.resolve("python3"), interpreter);
    Files.createSymbolicLink(bin.resolve("python"), interpreter);
    final Map<String, String> env = new HashMap<>();
    env.put("PATH", bin + ":" + System.getenv("PATH"));
    env.put("UV_PYTHON", python);
    env.put("UV_PYTHON_DOWNLOADS", "never");
    return env;
  }

  /** Returns the interpreter uv finds for {@code python}. */
  private static String find(final String python) throws IOException, InterruptedException {
    final Run found = exec(Map.of(), Path.of("."), List.of("uv", "python", "find", python));
    assertThat(found.exit()).as("uv python find %s", python).isZero();
    return found.output().strip();
  }

  /**
   * Creates the manager's environment the way a user would before init: pip gets a venv, Poetry an
   * env.
   */
  private static void prepareManager(
      final String manager,
      final String interpreter,
      final Path project,
      final Map<String, String> env)
      throws IOException, InterruptedException {
    if (PIP.equals(manager)) {
      assertThat(exec(env, project, List.of(interpreter, "-m", "venv", ".venv")).exit()).isZero();
      assertThat(
              exec(
                      env,
                      project,
                      List.of(
                          project.resolve(".venv/bin/python").toString(),
                          "-m",
                          "pip",
                          "install",
                          "--upgrade",
                          "pip"))
                  .exit())
          .isZero();
    } else if (POETRY.equals(manager)) {
      assertThat(exec(env, project, List.of("poetry", "env", "use", interpreter)).exit()).isZero();
    }
  }

  private Run run(final Map<String, String> env, final Path project, final String... args)
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
