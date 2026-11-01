package dev.ward.application.init;

import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptReplacement;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Init choices for npm, pnpm, Yarn and Bun projects. */
final class NodeInitSupport implements ToolchainInitSupport {
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
  public Set<String> partialGates() {
    return NodePartialTables.GATES;
  }

  /** A Turborepo root runs its lint gates through turbo; other projects use their own tables. */
  @Override
  public Optional<Table> partialTable(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return TurboPartialDefaults.forGate(gate, project)
        .or(() -> NodePartialTables.forGate(gate, project, preset));
  }

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner().isRootRunner()) {
      return NodeGates.turborepo(project, preset, inputs);
    }
    final NodeTools tools =
        NodeTools.of(project, inputs.manifests().read(project.root()), inputs, preset);
    NodeGates.refuseBiomeForAngular(project, tools);
    return new Gates(
        NodeGates.languages(project, tools.testGate(), tools.linting()).analysisGates(tools),
        List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner().isRootRunner()) {
      final NodeLinting linting =
          NodeLinting.of(project, inputs.manifests().read(project.root()), inputs.configs());
      return NodeGates.languages(project, TurborepoPlan.testGate(project, inputs), linting)
          .presetGates(preset);
    }
    final PackageManifest manifest = inputs.manifests().read(project.root());
    final String testGate = NodeTools.testGate(project, manifest);
    final NodeLinting linting = NodeLinting.of(project, manifest, inputs.configs());
    return NodeGates.languages(project, testGate, linting).presetGates(preset);
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
