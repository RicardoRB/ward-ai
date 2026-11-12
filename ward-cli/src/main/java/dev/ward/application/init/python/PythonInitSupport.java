package dev.ward.application.init.python;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.CrapInit;
import dev.ward.application.init.CrapSetup;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Init choices for uv, Poetry and pip projects. PDM and Hatch projects are not supported yet. */
public final class PythonInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<PythonTools>> LANGUAGES =
      Map.of(Language.PYTHON, new PythonGates());

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final PythonTools python =
        PythonTools.of(
            project,
            inputs.pythonConfigs(),
            inputs
                .environment()
                .inspect(project.root())
                .hasExecutable(PythonTools.executable(project)),
            preset);
    return new Gates(SourceLanguages.of(LANGUAGES, project).analysisGates(python), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final List<String> gates =
        SourceLanguages.of(LANGUAGES, project)
            .presetGates(preset == null ? QualityPreset.STANDARD : preset);
    return project.framework() == Framework.DJANGO ? withDjangoCheck(gates) : gates;
  }

  /** Only the managers Ward installs through; other Python managers fail init with their name. */
  /** Django's system checks run in both presets, after the gates of the language. */
  private static List<String> withDjangoCheck(final List<String> gates) {
    return java.util.stream.Stream.concat(
            gates.stream(), java.util.stream.Stream.of("django-check"))
        .toList();
  }

  @Override
  public Optional<CrapSetup> crap(final Project project, final InitInputs inputs) {
    return Optional.of(
        gates ->
            CrapInit.place(
                gates,
                "pytest",
                List.of("coverage"),
                List.of(),
                Map.of("report", PythonGates.REPORT)));
  }

  @Override
  public boolean handles(final Project project) {
    return project.buildTool() == BuildTool.UV
        || project.buildTool() == BuildTool.POETRY
        || project.buildTool() == BuildTool.PIP;
  }

  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.PYTHON && handles(project);
  }

  @Override
  public List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    return project.toolchain() == Toolchain.PYTHON
        ? PythonAgentRules.lines(project, gates, preset, owned)
        : List.of();
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return PythonCiSetup.steps(project);
  }
}
