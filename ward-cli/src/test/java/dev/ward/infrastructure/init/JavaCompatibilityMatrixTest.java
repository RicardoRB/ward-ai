package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Runs each Java compatibility matrix cell through the real CLI on the cell's JDK: {@code init},
 * the clean {@code check}, the seeded violations, which must fail every configured gate except
 * {@code compile}, {@code coverage} and {@code mutation}, and a compile error, which must fail
 * {@code compile}. Runs only the cells of the JDK named by {@code ward.java.matrix}, with the Ward
 * jar named by {@code ward.java.matrix.jar} and that JDK's home named by {@code
 * ward.java.matrix.home}. Ward runs on the test JVM; Maven, Gradle and every gate run on the cell's
 * JDK.
 */
@EnabledIfSystemProperty(named = JavaCompatibilityMatrixTest.JDK_PROPERTY, matches = "\\d+")
class JavaCompatibilityMatrixTest {
  static final String JDK_PROPERTY = "ward.java.matrix";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/java-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/java-matrix/violations");
  private static final String MAVEN = "maven";
  private static final String POM = "pom.xml";
  private static final String PASSED = "PASSED";
  private static final String FAILED = "FAILED";
  private static final String STRICT = "strict";
  private static final int RECORDS = 17;
  private static final int PATTERN_SWITCH = 21;
  private static final List<String> STANDARD =
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage");
  private static final List<String> STRICT_EXTRAS = List.of("archunit", "enforcer", "mutation");

  /** Gates the seeded violations do not target: the clean check proves the last two measure. */
  private static final Set<String> NOT_SEEDED = Set.of("compile", "coverage", "mutation");

  private static final Pattern CHECK =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z]+)\"");
  private static final Pattern CONFIGURED = Pattern.compile("\\[checks\\.([a-z-]+)\\]");
  private static final Pattern DETECTED = Pattern.compile("JDK (\\d+) runs the build");

  @ParameterizedTest(name = "jdk {0} {1} {2} {3}")
  @CsvFileSource(resources = "/java-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell, @TempDir final Path project)
      throws IOException, InterruptedException {
    final int jdk = cell.getInteger(0);
    final String buildTool = cell.getString(1);
    final String dsl = Objects.requireNonNullElse(cell.getString(2), "");
    final String preset = cell.getString(3);
    Assumptions.assumeTrue(
        String.valueOf(jdk).equals(System.getProperty(JDK_PROPERTY)), "row is for JDK " + jdk);

    copyFixture(project, jdk, MAVEN.equals(buildTool) ? MAVEN : "gradle-" + dsl);
    JavaMatrixProcesses.git(project, "init", "-q");
    final JavaMatrixProcesses.CommandRun init =
        JavaMatrixProcesses.ward(project, "init", "--preset", preset, "--yes");
    assertThat(init.exitCode()).as("ward init exit code\n%s", init.stdout()).isZero();
    assertDetectedJdk(project, jdk);
    final Set<String> configured = configuredGates(project);
    assertOmissionsExplained(preset, configured, init.stdout());
    JavaMatrixProcesses.git(project, "add", "-A");
    JavaMatrixProcesses.git(
        project,
        "-c",
        "user.name=ward",
        "-c",
        "user.email=ward@example.invalid",
        "commit",
        "-qm",
        "init");

    final Map<String, String> clean = statuses(project);
    assertThat(clean.keySet()).as("checked gates").containsExactlyInAnyOrderElementsOf(configured);
    clean.forEach((gate, status) -> assertThat(status).as("clean gate %s", gate).isEqualTo(PASSED));

    seedViolations(project, buildTool);
    final Map<String, String> seeded = statuses(project);
    configured.stream()
        .filter(gate -> !NOT_SEEDED.contains(gate))
        .forEach(
            gate ->
                assertThat(seeded.get(gate))
                    .as("gate that runs nothing is caught: %s", gate)
                    .isEqualTo(FAILED));

    JavaMatrixProcesses.git(project, "checkout", "-q", ".");
    JavaMatrixProcesses.git(project, "clean", "-qfd");
    JavaMatrixProcesses.copyTree(VIOLATIONS.resolve("compile-error"), project);
    assertThat(statuses(project).get("compile"))
        .as("compile with a compile error")
        .isEqualTo(FAILED);
  }

  /** Copies the Java 8 sources, each newer syntax layer the JDK supports, and the build files. */
  private static void copyFixture(final Path project, final int jdk, final String build)
      throws IOException {
    JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/base"), project);
    if (jdk >= RECORDS) {
      JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/java17"), project);
    }
    if (jdk >= PATTERN_SWITCH) {
      JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/java21"), project);
    }
    JavaMatrixProcesses.copyTree(FIXTURES.resolve(build), project);
    try (Stream<Path> files = Files.list(project)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final String content = Files.readString(file);
        if (content.contains("@RELEASE@")) {
          Files.writeString(file, content.replace("@RELEASE@", String.valueOf(jdk)));
        }
      }
    }
  }

  private static void assertDetectedJdk(final Path project, final int jdk)
      throws IOException, InterruptedException {
    final Matcher detected = DETECTED.matcher(JavaMatrixProcesses.ward(project, "doctor").stdout());
    final String reported = detected.find() ? detected.group(1) : "none";
    assertThat(reported)
        .as("Wrong runtime: the cell needs JDK %d, the build tool reports JDK %s", jdk, reported)
        .isEqualTo(String.valueOf(jdk));
  }

  /** Every preset gate init left out of {@code ward.toml} must be named with the JDK it needs. */
  private static void assertOmissionsExplained(
      final String preset, final Set<String> configured, final String output) {
    final List<String> expected = new ArrayList<>(STANDARD);
    if (STRICT.equals(preset)) {
      expected.addAll(STRICT_EXTRAS);
    }
    expected.stream()
        .filter(gate -> !configured.contains(gate))
        .forEach(
            gate ->
                assertThat(output)
                    .as("unexplained missing gate: %s", gate)
                    .containsPattern(Pattern.quote(gate + " omitted: ") + ".*needs JDK \\d+\\+"));
  }

  private static Set<String> configuredGates(final Path project) throws IOException {
    final Set<String> gates = new LinkedHashSet<>();
    final Matcher matcher = CONFIGURED.matcher(Files.readString(project.resolve("ward.toml")));
    while (matcher.find()) {
      gates.add(matcher.group(1));
    }
    return gates;
  }

  /** Adds the common violations, and for Maven a dynamic dependency version for Enforcer. */
  private static void seedViolations(final Path project, final String buildTool)
      throws IOException {
    JavaMatrixProcesses.copyTree(VIOLATIONS.resolve("common"), project);
    if (MAVEN.equals(buildTool)) {
      final Path pom = project.resolve(POM);
      final String dependency = Files.readString(VIOLATIONS.resolve("maven/dependency.xml"));
      Files.writeString(
          pom, Files.readString(pom).replaceFirst("(?s)(\\s*</dependencies>)", dependency + "$1"));
    }
  }

  private static Map<String, String> statuses(final Path project)
      throws IOException, InterruptedException {
    final Map<String, String> statuses = new LinkedHashMap<>();
    final Matcher matcher =
        CHECK.matcher(
            JavaMatrixProcesses.ward(project, "check", "--profile", "ci", "--format", "json")
                .stdout());
    while (matcher.find()) {
      statuses.put(matcher.group(1), matcher.group(2));
    }
    return statuses;
  }
}
