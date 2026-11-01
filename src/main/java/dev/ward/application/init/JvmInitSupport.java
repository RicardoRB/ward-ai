package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Init choices for Maven and Gradle projects. */
final class JvmInitSupport implements ToolchainInitSupport {
  private static final JavaGates JAVA = new JavaGates();
  private static final int DEFAULT_CI_JDK = 25;
  private static final String ARCHUNIT = "archunit";
  private static final String COVERAGE = "coverage";
  private static final String MULTI_MODULE_NO_ARCHUNIT =
      "archunit is not offered for this Gradle build: its modules already enforce the dependency"
          + " direction between them.";
  private static final String SKIPPED_MODULES =
      "These modules are left out of the gates that init places per module, because they have no"
          + " Java sources or apply no Java or Kotlin JVM plugin: ";
  private static final List<String> ANDROID_GATES =
      List.of(
          SupportedQualityGates.ANDROID_LINT,
          SupportedQualityGates.ANDROID_UNIT_TEST,
          SupportedQualityGates.ANDROID_COVERAGE);
  // shortcut: Kotlin gets Java's tools until Ward ships ktlint/detekt; give it its own gates then.
  private static final Map<Language, SourceLanguageGates<BuildToolGates>> LANGUAGES =
      Map.of(Language.JAVA, JAVA, Language.KOTLIN, JAVA);

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final BuildToolGates buildTool = BuildToolGates.of(project, inputs.environment());
    final boolean multiModule = multiModuleKotlin(project, inputs.environment());
    final boolean android = project.framework() == Framework.ANDROID;
    return new Gates(
        analysisGates(project, buildTool, multiModule, android),
        buildTool.policyGates().stream()
            .filter(gate -> !"api-compat".equals(gate.id()) || project.hasApiBaseline())
            .filter(gate -> !(multiModule && ARCHUNIT.equals(gate.id())))
            .toList());
  }

  /**
   * The language's analysis gates, with Android's own gates in place of JaCoCo's coverage gate on
   * an Android project, whose root build has no JaCoCo verification task.
   */
  private static List<InitGateOption> analysisGates(
      final Project project,
      final BuildToolGates buildTool,
      final boolean multiModule,
      final boolean android) {
    final List<InitGateOption> gates =
        SourceLanguages.of(LANGUAGES, project).analysisGates(buildTool).stream()
            .filter(gate -> !(multiModule && ARCHUNIT.equals(gate.id())))
            .filter(gate -> !(android && COVERAGE.equals(gate.id())))
            .collect(Collectors.toCollection(ArrayList::new));
    if (android) {
      gates.addAll(buildTool.androidGates());
    }
    return gates;
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final boolean multiModule = multiModuleKotlin(project, inputs.environment());
    final boolean android = project.framework() == Framework.ANDROID;
    final List<String> gates =
        SourceLanguages.of(LANGUAGES, project).presetGates(preset).stream()
            .filter(gate -> !(multiModule && ARCHUNIT.equals(gate)))
            .filter(gate -> !(android && COVERAGE.equals(gate)))
            .collect(Collectors.toCollection(ArrayList::new));
    if (android) {
      gates.addAll(ANDROID_GATES);
    }
    return gates;
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

  @Override
  public List<InitGateOption> reuseTests(
      final Project project, final List<InitGateOption> gates, final List<String> tests) {
    return ArchUnitGateReuse.rewrite(project, gates, tests);
  }

  @Override
  public List<GateOmission> omissions(
      final Project project, final List<String> gateIds, final InitInputs inputs) {
    return new JdkGates(inputs).omissions(project, gateIds);
  }

  @Override
  public List<String> notes(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final List<String> notes = new ArrayList<>();
    final EnvironmentInspection environment = inputs.environment();
    if (preset == QualityPreset.STRICT && multiModuleKotlin(project, environment)) {
      notes.add(MULTI_MODULE_NO_ARCHUNIT);
    }
    final List<String> skipped = environment.skippedJvmModules(project.root());
    if (!skipped.isEmpty()) {
      notes.add(SKIPPED_MODULES + String.join(", ", skipped) + ".");
    }
    if (preset != null) {
      new JdkGates(inputs)
          .presetOmissions(project, preset).stream().map(GateOmission::note).forEach(notes::add);
    }
    return notes;
  }

  @Override
  public Set<String> partialGates() {
    return JvmPartialTables.GATES;
  }

  @Override
  public Optional<PartialCheckDefaults.Table> partialTable(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return JvmPartialTables.forGate(gate, project, preset);
  }
}
