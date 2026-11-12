package dev.ward.application.init.jvm;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
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
import java.util.stream.Collectors;

/** Init choices for Maven and Gradle projects. */
public final class JvmInitSupport implements ToolchainInitSupport {
  private static final JavaGates JAVA = new JavaGates();
  private static final int DEFAULT_CI_JDK = 25;
  private static final String COVERAGE = "coverage";
  private static final String SKIPPED_MODULES =
      "These modules are left out of the gates that init places per module, because they have no"
          + " Java sources or apply no Java or Kotlin JVM plugin: ";
  private static final List<String> ANDROID_GATES =
      List.of(
          SupportedQualityGates.ANDROID_LINT,
          SupportedQualityGates.ANDROID_UNIT_TEST,
          SupportedQualityGates.ANDROID_COVERAGE);
  private static final Map<Language, SourceLanguageGates<BuildToolGates>> LANGUAGES =
      Map.of(Language.JAVA, JAVA, Language.KOTLIN, JAVA);

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final BuildToolGates buildTool = BuildToolGates.of(project, inputs.environment());
    final boolean android = project.framework() == Framework.ANDROID;
    return new Gates(
        analysisGates(project, buildTool, android),
        buildTool.policyGates().stream()
            .map(gate -> apiCompat(gate, project, inputs))
            .flatMap(Optional::stream)
            .toList());
  }

  /**
   * Java projects get the native source comparison when a release tag exists; Kotlin projects keep
   * japicmp, which needs a published baseline, until the native check reads Kotlin.
   */
  private static Optional<InitGateOption> apiCompat(
      final InitGateOption gate, final Project project, final InitInputs inputs) {
    if (!SupportedQualityGates.API_COMPAT.equals(gate.id())) {
      return Optional.of(gate);
    }
    if (project.language() == Language.JAVA) {
      return inputs.environment().hasReleaseTag(project.root())
          ? Optional.of(
              new InitGateOption(
                  gate.id(),
                  "Compares the public API with the latest release tag, from sources.",
                  List.of(),
                  true,
                  "compares sources with the latest release tag; needs full Git history and tags"))
          : Optional.empty();
    }
    return project.hasApiBaseline() ? Optional.of(gate) : Optional.empty();
  }

  /**
   * The language's analysis gates, with Android's own gates in place of JaCoCo's coverage gate on
   * an Android project, whose root build has no JaCoCo verification task.
   */
  private static List<InitGateOption> analysisGates(
      final Project project, final BuildToolGates buildTool, final boolean android) {
    final List<InitGateOption> gates =
        SourceLanguages.of(LANGUAGES, project).analysisGates(buildTool).stream()
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
    final boolean android = project.framework() == Framework.ANDROID;
    final List<String> gates =
        SourceLanguages.of(LANGUAGES, project).presetGates(preset).stream()
            .filter(gate -> !(android && COVERAGE.equals(gate)))
            .collect(Collectors.toCollection(ArrayList::new));
    if (android) {
      gates.addAll(ANDROID_GATES);
    }
    return gates;
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
  public List<GateOmission> omissions(
      final Project project, final List<String> gateIds, final InitInputs inputs) {
    return new JdkGates(inputs).omissions(project, gateIds);
  }

  @Override
  public List<String> notes(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final List<String> notes = new ArrayList<>();
    final EnvironmentInspection environment = inputs.environment();
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
}
