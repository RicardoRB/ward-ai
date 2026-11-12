package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.LayerPorts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each Java compatibility matrix cell through the real CLI on the cell's JDK: {@code init},
 * the clean {@code check}, the seeded violations, which must fail every configured gate except
 * {@code compile}, {@code coverage} and {@code mutation}, a compile error, which must fail {@code
 * compile}, and an untested class, which must fail {@code coverage}. Runs only the cells of the JDK
 * named by {@code ward.java.matrix}, with the Ward jar named by {@code ward.java.matrix.jar} and
 * that JDK's home named by {@code ward.java.matrix.home}. Ward runs on the test JVM; Maven, Gradle
 * and every gate run on the cell's JDK.
 */
@EnabledIfSystemProperty(named = JavaCompatibilityMatrixTest.JDK_PROPERTY, matches = "\\d+")
class JavaCompatibilityMatrixTest {
  static final String JDK_PROPERTY = "ward.java.matrix";

  /** The controller that imports a repository directly, which the layered style forbids. */
  private static final String ENGINE_LAYER_VIOLATION =
      "src/main/java/demo/controller/DirectController.java";

  private static final String MAVEN = "maven";
  private static final String WARD_GATE = "ward";
  private static final String PASSED = "PASSED";
  private static final String FAILED = "FAILED";
  private static final String STRICT = "strict";
  private static final List<String> STANDARD =
      List.of("compile", "tests", "coverage", "ward-format");
  private static final List<String> STRICT_EXTRAS = List.of("enforcer", "mutation");

  /**
   * Gates the combined violations do not target: the compile error and the untested class have
   * their own runs, and the clean check proves {@code mutation} measures.
   */
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
    final String fixture = Objects.requireNonNullElse(cell.getString(4), "");
    Assumptions.assumeTrue(
        String.valueOf(jdk).equals(System.getProperty(JDK_PROPERTY)), "row is for JDK " + jdk);

    final String build = MAVEN.equals(buildTool) ? MAVEN : "gradle-" + dsl;
    JavaMatrixFixtures.copyFixture(project, jdk, build, fixture);
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

    final String cleanReport = report(project);
    final Map<String, String> clean = statuses(cleanReport);
    assertThat(clean.keySet()).as("checked gates").containsExactlyInAnyOrderElementsOf(configured);
    clean.forEach(
        (gate, status) ->
            assertThat(status).as("clean gate %s\n%s", gate, cleanReport).isEqualTo(PASSED));

    seed(project, buildTool, fixture);
    final Map<String, String> seeded = statuses(report(project));
    seededGates(configured, fixture)
        .forEach(
            gate ->
                assertThat(seeded.get(gate))
                    .as("gate that runs nothing is caught: %s", gate)
                    .isEqualTo(FAILED));

    JavaMatrixProcesses.git(project, "checkout", "-q", ".");
    JavaMatrixProcesses.git(project, "clean", "-qfd");
    JavaMatrixProcesses.copyTree(
        JavaMatrixFixtures.VIOLATIONS.resolve(JavaMatrixFixtures.compileError(fixture)), project);
    assertThat(statuses(report(project)).get("compile"))
        .as("compile with a compile error")
        .isEqualTo(FAILED);

    JavaMatrixProcesses.git(project, "checkout", "-q", ".");
    JavaMatrixProcesses.git(project, "clean", "-qfd");
    JavaMatrixProcesses.copyTree(
        JavaMatrixFixtures.VIOLATIONS.resolve(JavaMatrixFixtures.coverageViolation(fixture)),
        project);
    final String untested = report(project);
    assertThat(statuses(untested).get("coverage"))
        .as("coverage with an untested class\n%s", untested)
        .isEqualTo(FAILED);
    assertThat(untested).as("coverage finding").contains("Line coverage").contains("is below");
  }

  /** Seeds the Java violations, with their layer check, or a named fixture's own violations. */
  private static void seed(final Path project, final String buildTool, final String fixture)
      throws IOException {
    if (!fixture.isEmpty()) {
      JavaMatrixProcesses.copyTree(JavaMatrixFixtures.VIOLATIONS.resolve(fixture), project);
      return;
    }
    assertThat(LayerPorts.layerFiles(javaProject(project), "layered", javaDependencies()))
        .as("engine layer findings, clean")
        .isEmpty();
    JavaMatrixFixtures.seedCommon(project, buildTool);
    assertThat(LayerPorts.layerFiles(javaProject(project), "layered", javaDependencies()))
        .as("engine layer findings, seeded")
        .containsExactly(ENGINE_LAYER_VIOLATION);
  }

  /**
   * The gates the seeded violations must fail: every configured gate but the ones they do not
   * target, or only the {@code ward} gate for a named fixture, whose violations are Kotlin rules.
   */
  private static Set<String> seededGates(final Set<String> configured, final String fixture) {
    if (fixture.isEmpty()) {
      return configured.stream()
          .filter(gate -> !NOT_SEEDED.contains(gate))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    return Set.of(WARD_GATE);
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

  private static Project javaProject(final Path project) {
    return new Project(project, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static ProjectDependencyAdapters javaDependencies() {
    return new ProjectDependencyAdapters(List.of(new JvmDependencyAdapters()));
  }

  private static String report(final Path project) throws IOException, InterruptedException {
    return JavaMatrixProcesses.ward(project, "check", "--profile", "ci", "--format", "json")
        .stdout();
  }

  private static Map<String, String> statuses(final String report) {
    final Map<String, String> statuses = new LinkedHashMap<>();
    final Matcher matcher = CHECK.matcher(report);
    while (matcher.find()) {
      statuses.put(matcher.group(1), matcher.group(2));
    }
    return statuses;
  }
}
