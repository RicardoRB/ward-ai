package dev.ward.application.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Init choices for uv, Poetry and pip projects. PDM and Hatch projects are not supported yet. */
final class PythonInitSupport implements ToolchainInitSupport {
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
    return SourceLanguages.of(LANGUAGES, project)
        .presetGates(preset == null ? QualityPreset.STANDARD : preset);
  }

  /** Only the managers Ward installs through; other Python managers fail init with their name. */
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
  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return PythonCiSetup.steps(project);
  }
}
