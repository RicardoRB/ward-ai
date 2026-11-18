package dev.ward.application.init.rust;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Init choices for Cargo crates and workspaces with Rust sources. */
public final class RustInitSupport implements ToolchainInitSupport {
  private static final Map<Language, SourceLanguageGates<RustTools>> LANGUAGES =
      Map.of(Language.RUST, new RustGates());

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final RustTools rust =
        new RustTools(
            inputs.environment().inspect(project.root()).hasExecutable(project.buildExecutable()),
            preset
        );
    return new Gates(SourceLanguages.of(LANGUAGES, project).analysisGates(rust), List.of());
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
  public boolean handles(final Project project) {
    return project.buildTool() == BuildTool.CARGO;
  }

  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.RUST && handles(project);
  }

  @Override
  public List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned
  ) {
    return RustAgentRules.lines(gates);
  }

  /** The GitHub-hosted Ubuntu runners ship rustup, cargo and Clippy, so no setup step is needed. */
  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    return "";
  }
}
