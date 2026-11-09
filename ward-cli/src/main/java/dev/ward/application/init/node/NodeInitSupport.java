package dev.ward.application.init.node;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.MemberPlanning;
import dev.ward.application.init.MemberWriter;
import dev.ward.application.init.ToolchainInitSupport;
import dev.ward.application.init.node.turbo.TurborepoMembers;
import dev.ward.application.init.node.turbo.TurborepoPlan;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptReplacement;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
import java.util.List;
import java.util.Set;

/** Init choices for npm, pnpm, Yarn and Bun projects. */
public final class NodeInitSupport implements ToolchainInitSupport {
  @Override
  public MemberWriter memberWriter(final InitProjectFiles files, final InitInputs inputs) {
    return new TurborepoMembers(files, inputs);
  }

  @Override
  public List<PlannedFileChange> planMembers(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved,
      final MemberPlanning planning) {
    return TurborepoMembers.planChanges(project, gates, preset, approved, planning);
  }

  @Override
  public List<ScriptReplacement> scriptReplacements(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    return TurborepoMembers.replacements(TurborepoMembers.plan(project, preset, inputs));
  }

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner().isRootRunner()) {
      return NodeGates.turborepo(project, preset, inputs);
    }
    final NodeTools tools =
        NodeTools.of(project, inputs.manifests().read(project.root()), inputs, preset);
    return new Gates(
        NodeGates.languages(project, tools.testGate()).analysisGates(tools), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner().isRootRunner()) {
      return NodeGates.languages(project, TurborepoPlan.testGate(project, inputs))
          .presetGates(preset);
    }
    final PackageManifest manifest = inputs.manifests().read(project.root());
    return NodeGates.languages(project, NodeTools.testGate(project, manifest)).presetGates(preset);
  }

  /** A JavaScript package without sources of its own, such as a monorepo root, is no project. */
  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.TYPESCRIPT
        || project.language() == Language.JAVASCRIPT && inputs.sources().hasSources(project.root());
  }

  @Override
  public List<String> agentRules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    return NodeAgentRules.lines(project, gates, preset, owned);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    final PackageManifest manifest = facts.manifest();
    return NodeCiSetup.steps(project, manifest.packageManager());
  }
}
