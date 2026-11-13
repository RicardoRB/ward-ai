package dev.ward.application.init.jvm;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gate choices that run through the project's Maven or Gradle executable. */
record BuildToolGates(
    boolean gradle,
    boolean available,
    String availability,
    String executable,
    QualityPreset preset) {
  /** The line coverage minimum of the Android coverage gate, in percent. */
  // shortcut: one minimum for both presets; strict should raise it to 85 like Swift.
  static final int ANDROID_COVERAGE_MINIMUM = 80;

  private static final String MINIMUM = "minimum";
  private static final String LINE = "LINE";
  private static final String BRANCH = "BRANCH";
  private static final double CUSTOM_COVERAGE = 0.80;
  private static final int PERCENT = 100;

  /** Runs the project's tests. */
  InitGateOption tests() {
    return option("tests", "Runs the project's tests.", "", "test");
  }

  /** Checks that the project compiles. */
  InitGateOption compile() {
    return option("compile", "Checks that the project compiles.", "", task("classes", "compile"));
  }

  /** Writes the JaCoCo report that Ward checks against the minimum in ward.toml. */
  InitGateOption coverage() {
    final InitGateOption report =
        option(
            "coverage",
            "Enforces minimum test coverage.",
            " (the minimum is configured in ward.toml; the build writes the JaCoCo XML report.)",
            task("jacocoTestReport", "jacoco:report"));
    return new InitGateOption(
        report.id(),
        report.description(),
        report.command(),
        report.available(),
        report.availabilityMessage(),
        coverageMinimums());
  }

  /**
   * The line minimum, and the branch minimum when the preset sets one, as whole percentages: the
   * preset's JaCoCo ratios, or 80% of lines for a custom selection.
   */
  private Map<String, String> coverageMinimums() {
    final Map<String, Double> ratios =
        preset == null ? Map.of(LINE, CUSTOM_COVERAGE) : preset.rules().jacocoMinimums();
    final Map<String, String> minimums = new LinkedHashMap<>();
    minimums.put(MINIMUM, percent(ratios.get(LINE)));
    if (ratios.containsKey(BRANCH)) {
      minimums.put("minimum-branches", percent(ratios.get(BRANCH)));
    }
    return minimums;
  }

  private static String percent(final double ratio) {
    return String.valueOf(Math.round(ratio * PERCENT));
  }

  /** Checks code quality, smells, and duplication with Sonar. */
  InitGateOption sonar() {
    return option("sonar", "Checks code quality, smells, and duplication.", "", "sonar");
  }

  /** Measures test strength with PIT mutation testing. */
  InitGateOption mutation() {
    return tool(
        "mutation",
        "Measures test strength with mutation testing.",
        "pitest",
        "pitest:mutationCoverage");
  }

  /** A source-language tool gate that runs {@code gradleTask} or {@code mavenTask}. */
  InitGateOption tool(
      final String id, final String description, final String gradleTask, final String mavenTask) {
    return option(id, description, "", task(gradleTask, mavenTask));
  }

  /**
   * Gates for an Android project's modules: Android Lint and the debug unit tests through AGP's
   * tasks, and coverage at {@link #ANDROID_COVERAGE_MINIMUM} percent.
   */
  List<InitGateOption> androidGates() {
    return List.of(
        tool(
            SupportedQualityGates.ANDROID_LINT,
            "Checks Android sources with Android Lint, debug variant.",
            "lintDebug",
            "lintDebug"),
        tool(
            SupportedQualityGates.ANDROID_UNIT_TEST,
            "Runs the debug unit tests.",
            "testDebugUnitTest",
            "testDebugUnitTest"),
        androidCoverage());
  }

  /** Gates that enforce build, compatibility, or custom policies, in wizard order. */
  List<InitGateOption> policyGates() {
    return List.of(
        buildPolicy(),
        apiCompat(),
        option(
            "command",
            "Runs a custom command from ward.toml.",
            " (customize the command in ward.toml after initialization.)",
            "test"));
  }

  /**
   * The {@code build-policy} gate: it has no command, because Ward reads the build files itself.
   */
  private static InitGateOption buildPolicy() {
    return new InitGateOption(
        SupportedQualityGates.BUILD_POLICY,
        "Checks pinned plugin versions, dynamic versions, the build tool version and duplicate"
            + " dependency declarations in the build files, with Ward's native rules.",
        List.of(),
        true,
        null,
        Map.of());
  }

  private static InitGateOption apiCompat() {
    return new InitGateOption(
        SupportedQualityGates.API_COMPAT,
        "Compares the public API with the latest release tag, from sources.",
        List.of(),
        true,
        null,
        Map.of());
  }

  private InitGateOption androidCoverage() {
    return new InitGateOption(
        SupportedQualityGates.ANDROID_COVERAGE,
        "Fails below the line coverage minimum of the debug unit tests.",
        List.of(executable, "createDebugUnitTestCoverageReport"),
        available,
        availability,
        Map.of(MINIMUM, String.valueOf(ANDROID_COVERAGE_MINIMUM)));
  }

  private String task(final String gradleTask, final String mavenTask) {
    return gradle ? gradleTask : mavenTask;
  }

  private InitGateOption option(
      final String id, final String description, final String note, final String... arguments) {
    final List<String> command = new ArrayList<>(List.of(arguments));
    command.addFirst(executable);
    return new InitGateOption(id, description, command, available, availability + note);
  }

  /** Returns the Maven or Gradle gate choices with the tool's availability resolved. */
  static BuildToolGates of(
      final Project project, final EnvironmentInspection environment, final QualityPreset preset) {
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final String executable = project.buildExecutable();
    final String toolName = gradle ? "Gradle" : "Maven";
    final String systemExecutable = gradle ? "gradle" : "mvn";
    final boolean toolAvailable =
        project.wrapperAvailable()
            || environment.inspect(project.root()).hasExecutable(systemExecutable);
    final String availability =
        toolAvailable
            ? toolName + " command available: " + executable
            : toolName
                + " is unavailable; install "
                + toolName
                + " or add a "
                + toolName
                + " Wrapper before running this gate.";
    return new BuildToolGates(gradle, toolAvailable, availability, executable, preset);
  }
}
