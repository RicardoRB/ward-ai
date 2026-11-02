package dev.ward.application.init.python;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** Init choices for uv, Poetry and pip projects. PDM and Hatch projects are not supported yet. */
public final class PythonInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<PythonTools>> LANGUAGES =
      Map.of(Language.PYTHON, new PythonGates());
  private static final List<String> PATHS = List.of("{paths}");
  private static final List<String> PYTHON_SOURCES = List.of("**/*.py", "**/*.pyi");

  // Their commands end with the target, so the narrowed command replaces it, as for Node.
  private static final Map<String, BiFunction<Project, QualityPreset, Table>> TABLES =
      Map.of(
          "ruff-format",
          (project, preset) ->
              new Table(
                  PYTHON_SOURCES,
                  List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                  PATHS,
                  null,
                  null,
                  PythonTools.command(project.buildTool(), "ruff", "format", "--check")),
          "ruff",
          (project, preset) ->
              new Table(
                  PYTHON_SOURCES,
                  List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                  PATHS,
                  null,
                  null,
                  PythonTools.command(
                      project.buildTool(), "ruff", "check", "--output-format", "json")));

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
  public Set<String> partialGates() {
    return TABLES.keySet();
  }

  @Override
  public Optional<Table> partialTable(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return Optional.ofNullable(TABLES.get(gate)).map(factory -> factory.apply(project, preset));
  }

  @Override
  public List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    return PythonAgentRules.lines(project, gates, preset, owned);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return PythonCiSetup.steps(project);
  }
}
