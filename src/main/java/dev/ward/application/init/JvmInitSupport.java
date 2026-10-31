package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Init choices for Maven and Gradle projects. */
final class JvmInitSupport implements ToolchainInitSupport {
  private static final JavaGates JAVA = new JavaGates();
  private static final int DEFAULT_CI_JDK = 25;
  private static final String ARCHUNIT = "archunit";
  // shortcut: Kotlin gets Java's tools until Ward ships ktlint/detekt; give it its own gates then.
  private static final Map<Language, SourceLanguageGates<BuildToolGates>> LANGUAGES =
      Map.of(Language.JAVA, JAVA, Language.KOTLIN, JAVA);

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final BuildToolGates buildTool = buildToolGates(project, inputs.environment());
    final boolean multiModule = multiModuleKotlin(project, inputs.environment());
    return new Gates(
        SourceLanguages.of(LANGUAGES, project).analysisGates(buildTool).stream()
            .filter(gate -> !(multiModule && ARCHUNIT.equals(gate.id())))
            .toList(),
        buildTool.policyGates().stream()
            .filter(gate -> !"api-compat".equals(gate.id()) || project.hasApiBaseline())
            .filter(gate -> !(multiModule && ARCHUNIT.equals(gate.id())))
            .toList());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final boolean multiModule = multiModuleKotlin(project, inputs.environment());
    return SourceLanguages.of(LANGUAGES, project).presetGates(preset).stream()
        .filter(gate -> !(multiModule && ARCHUNIT.equals(gate)))
        .toList();
  }

  /**
   * Returns whether a Gradle build has a Kotlin module below its root. Its module boundaries
   * already enforce the dependency direction, so ArchUnit is not offered there.
   */
  static boolean multiModuleKotlin(final Project project, final EnvironmentInspection environment) {
    return project.buildTool() == BuildTool.GRADLE
        && !environment.gradleKotlinModules(project.root()).isEmpty();
  }

  @Override
  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return CiWorkflowTemplate.STEP
        + "Set up JDK\n"
        + "        uses: actions/setup-java@v5\n"
        + "        with:\n"
        + "          distribution: temurin\n"
        // The pinned tools run only on the JDK init chose them for.
        + "          java-version: '"
        + facts.runtimeJdk().orElse(DEFAULT_CI_JDK)
        + "'\n"
        + "          cache: "
        + (project.buildTool() == BuildTool.GRADLE ? "gradle" : "maven")
        + "\n";
  }

  private static BuildToolGates buildToolGates(
      final Project project, final EnvironmentInspection environment) {
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
    return new BuildToolGates(gradle, toolAvailable, availability, executable);
  }
}
