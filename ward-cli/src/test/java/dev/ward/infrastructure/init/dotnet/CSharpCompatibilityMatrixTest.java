package dev.ward.infrastructure.init.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.init.MatrixFileTrees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each C# matrix cell through the real CLI: {@code init}, the clean {@code check}, then an
 * untested class, which must make {@code coverage} fail, then the seeded violations, which must
 * make every selected gate except {@code coverage}, {@code stryker-net} and {@code dotnet-build}
 * fail, and a compile error, which must make {@code dotnet-build} fail. Runs only for the cells
 * whose SDK major is named by {@code ward.csharp.matrix}; the SDK on the PATH must be that major.
 * {@code ward.csharp.matrix.fixture} limits the run to one fixture.
 */
@EnabledIfSystemProperty(named = CSharpCompatibilityMatrixTest.SDK_PROPERTY, matches = "\\d+")
class CSharpCompatibilityMatrixTest {
  static final String SDK_PROPERTY = "ward.csharp.matrix";
  private static final String FIXTURE_PROPERTY = "ward.csharp.matrix.fixture";
  private static final String JAR_PROPERTY = "ward.csharp.matrix.jar";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/csharp-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/csharp-matrix/violations");
  private static final Path COVERAGE = Path.of("src/test/resources/csharp-matrix/coverage");
  private static final Set<String> NOT_VIOLATED = Set.of("coverage", "stryker-net", "dotnet-build");
  private static final String PASSED = "PASSED";
  private static final String FAILED = "FAILED";
  private static final List<String> PROJECTS =
      List.of("src/Shop/Shop.csproj", "tests/Shop.Tests/Shop.Tests.csproj");
  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z_]+)\"");

  @ParameterizedTest(name = "dotnet {0} {1} {2}")
  @CsvFileSource(resources = "/csharp-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell,
      @TempDir final Path project
  ) throws IOException, InterruptedException {
    final String sdk = cell.getString(0);
    final String preset = cell.getString(1);
    final String fixture = cell.getString(2);
    Assumptions.assumeTrue(sdk.equals(System.getProperty(SDK_PROPERTY)), "row is for SDK " + sdk);
    Assumptions.assumeTrue(
        fixture.equals(System.getProperty(FIXTURE_PROPERTY, fixture)),
        "row is for " + fixture
    );
    assertThat(exec(project, List.of("dotnet", "--version")).output())
        .as("the SDK on the PATH")
        .startsWith(sdk + ".");
    MatrixFileTrees.copy(FIXTURES.resolve(fixture), project);
    targetFramework(project, "net" + sdk + ".0");
    // The formatter and the gates read .gitignore through Git, as they do in a real project.
    assertThat(exec(project, List.of("git", "init", "-q")).exit()).isZero();

    final Run init = ward(project, "init", "--preset", preset, "--integration", "none", "--yes");
    assertThat(init.exit()).as("init output:\n%s", init.output()).isZero();
    assertThat(exec(project, List.of("dotnet", "tool", "restore")).exit()).isZero();
    final Map<String, String> clean = check(project);
    assertThat(clean).as("clean check").isNotEmpty();
    assertThat(clean.entrySet().stream().filter(gate -> !PASSED.equals(gate.getValue())).toList())
        .as("gates that fail on the clean project")
        .isEmpty();

    assertUntestedClassFailsCoverage(project, fixture);
    MatrixFileTrees.copy(VIOLATIONS.resolve(fixture), project);
    assertSeededViolations(project, clean.keySet());
    MatrixFileTrees.copy(VIOLATIONS.resolve(fixture + "-compile-error"), project);
    assertThat(check(project).get("dotnet-build")).as("compile error").isEqualTo(FAILED);
  }

  private static void assertSeededViolations(final Path project, final Set<String> gates)
      throws IOException, InterruptedException {
    final Map<String, String> violated = check(project);
    gates.stream()
        .filter(gate -> !NOT_VIOLATED.contains(gate))
        .forEach(
            gate ->
                assertThat(violated.get(gate))
                    .as("seeded violation for gate %s", gate)
                    .isEqualTo(FAILED)
        );
  }

  private static void assertUntestedClassFailsCoverage(final Path project, final String fixture)
      throws IOException, InterruptedException {
    final Path untested = COVERAGE.resolve(fixture);
    MatrixFileTrees.copy(untested, project);
    final Run report = ward(project, "check", "--profile", "ci", "--format", "json");
    assertThat(checks(report).get("coverage"))
        .as("coverage with an untested class\n%s", report.output())
        .isEqualTo(FAILED);
    assertThat(report.output()).as("coverage finding").contains("Line coverage", "is below");
    MatrixFileTrees.remove(untested, project);
  }

  /** Sets the target framework of the fixture's projects to the SDK of the cell. */
  private static void targetFramework(final Path project, final String framework)
      throws IOException {
    for (final String file : PROJECTS) {
      final Path csproj = project.resolve(file);
      Files.writeString(
          csproj,
          Files.readString(csproj).replace(
              "<TargetFramework>net8.0<",
              "<TargetFramework>" + framework + "<"
          )
      );
    }
  }

  private static Map<String, String> check(final Path project)
      throws IOException, InterruptedException {
    return checks(ward(project, "check", "--profile", "ci", "--format", "json"));
  }

  private static Run ward(final Path project, final String... args)
      throws IOException, InterruptedException {
    final List<String> command =
        new ArrayList<>(
            List.of(
                "java",
                "-jar",
                Path.of(System.getProperty(JAR_PROPERTY)).toAbsolutePath().toString()
            )
        );
    command.addAll(List.of(args));
    return exec(project, command);
  }

  private static Run exec(final Path dir, final List<String> command)
      throws IOException, InterruptedException {
    final ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
    builder.redirectErrorStream(true);
    final Process process = builder.start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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

  /** The exit code and output of one process. */
  private record Run(int exit, String output) {}
}
