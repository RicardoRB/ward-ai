package dev.ward.application.init.dotnet;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Init choices for C# solutions and projects built with the .NET SDK. The build runs from the
 * solution root, so every project the solution lists is built and tested.
 */
public final class DotnetInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<DotnetTools>> LANGUAGES =
      Map.of(Language.CSHARP, new DotnetGates());

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final DotnetTools dotnet = tools(project, preset, inputs);
    return new Gates(SourceLanguages.of(LANGUAGES, project).analysisGates(dotnet), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project,
      final QualityPreset preset,
      final InitInputs inputs
  ) {
    return SourceLanguages.of(LANGUAGES, project).presetGates(preset);
  }

  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.CSHARP;
  }

  @Override
  public Optional<String> gitignoreEntries(
      final Project project,
      final List<InitGateOption> gates
  ) {
    return Optional.of(".ward/reports/\n");
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return "      - uses: actions/setup-dotnet@v4\n        with:\n          dotnet-version: 9.0.x\n";
  }

  private static DotnetTools tools(
      final Project project,
      final QualityPreset preset,
      final InitInputs inputs
  ) {
    return new DotnetTools(
        inputs.environment().inspect(project.root()).hasExecutable(project.buildExecutable()),
        preset
    );
  }
}
