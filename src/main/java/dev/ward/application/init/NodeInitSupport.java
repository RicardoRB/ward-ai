package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.util.List;
import java.util.Map;

/** Init choices for npm, pnpm, Yarn and Bun projects. */
final class NodeInitSupport implements ToolchainInitSupport {

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner() == TaskRunner.TURBO) {
      return turborepoGates(project, preset, inputs);
    }
    final NodeTools tools =
        NodeTools.of(project, inputs.manifests().read(project.root()), inputs, preset);
    refuseBiomeForAngular(project, tools);
    return new Gates(
        languages(project, tools.testGate(), tools.linting()).analysisGates(tools), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    if (project.taskRunner() == TaskRunner.TURBO) {
      final NodeLinting linting =
          NodeLinting.of(project, inputs.manifests().read(project.root()), inputs.configs());
      return languages(project, TurborepoPlan.testGate(project, inputs), linting)
          .presetGates(preset);
    }
    final PackageManifest manifest = inputs.manifests().read(project.root());
    final String testGate = NodeTools.testGate(project, manifest);
    final NodeLinting linting = NodeLinting.of(project, manifest, inputs.configs());
    return languages(project, testGate, linting).presetGates(preset);
  }

  /**
   * Returns the root's gates of a Turborepo workspace: the task gates run through turbo, with the
   * test gate named after the members' majority runner.
   */
  private static Gates turborepoGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final PackageManifest manifest = inputs.manifests().read(project.root());
    final NodeLinting linting = NodeLinting.of(project, manifest, inputs.configs());
    final String testGate = TurborepoPlan.testGate(project, inputs);
    final NodeTools tools =
        NodeTools.of(project, manifest, inputs, preset).withTestGate(testGate).withLinting(linting);
    refuseBiomeForAngular(project, tools);
    return new Gates(
        TurborepoPlan.rootGates(
            languages(project, testGate, linting).analysisGates(tools), project.buildTool()),
        List.of());
  }

  /** Biome has no Angular template support, so a green Biome gate would hide template errors. */
  static void refuseBiomeForAngular(final Project project, final NodeTools tools) {
    if (project.framework() == Framework.ANGULAR && tools.linting().biome()) {
      throw new WardException(
          "Biome does not check Angular templates, so a passing biome gate would hide template"
              + " errors. Lint this Angular project with eslint instead: remove biome from the"
              + " project, then run init again.");
    }
  }

  /** A JavaScript package without sources of its own, such as a monorepo root, is no project. */
  @Override
  public boolean supports(final Project project, final InitInputs inputs) {
    return project.language() == Language.TYPESCRIPT
        || project.language() == Language.JAVASCRIPT && inputs.sources().hasSources(project.root());
  }

  @Override
  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Override
  public String ciSetup(final Project project, final CiWorkflowTemplate.ProjectFacts facts) {
    final PackageManifest manifest = facts.manifest();
    return NodeCiSetup.steps(project, manifest.packageManager());
  }

  static SourceLanguages<NodeTools> languages(
      final Project project, final String testGate, final NodeLinting linting) {
    return SourceLanguages.of(
        Map.<Language, SourceLanguageGates<NodeTools>>of(
            Language.TYPESCRIPT,
            new TypeScriptGates(project.framework(), testGate, linting),
            Language.JAVASCRIPT,
            new JavaScriptGates(testGate, linting)),
        project);
  }
}
