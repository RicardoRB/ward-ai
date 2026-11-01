package dev.ward.application.init.node;

import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport.Gates;
import dev.ward.application.init.node.turbo.TaskRunnerStrategies;
import dev.ward.application.init.node.turbo.TurborepoPlan;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Node gate choices that the support's dispatch shares: Turborepo roots and the language gates. */
public final class NodeGates {
  private NodeGates() {}

  /**
   * Returns the root's gates of a Turborepo workspace: the task gates run through turbo, with the
   * test gate named after the members' majority runner.
   */
  static Gates turborepo(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final PackageManifest manifest = inputs.manifests().read(project.root());
    final NodeLinting linting = NodeLinting.of(project, manifest, inputs.configs());
    final String testGate = TurborepoPlan.testGate(project, inputs);
    final NodeTools tools =
        NodeTools.of(project, manifest, inputs, preset).withTestGate(testGate).withLinting(linting);
    refuseBiomeForAngular(project, tools);
    return new Gates(
        TurborepoPlan.rootGates(
            languages(project, testGate, linting).analysisGates(tools),
            TaskRunnerStrategies.of(project).orElseThrow(),
            project.buildTool()),
        List.of());
  }

  /** Biome has no Angular template support, so a green Biome gate would hide template errors. */
  public static void refuseBiomeForAngular(final Project project, final NodeTools tools) {
    if (project.framework() == Framework.ANGULAR && tools.linting().biome()) {
      throw new WardException(
          "Biome does not check Angular templates, so a passing biome gate would hide template"
              + " errors. Lint this Angular project with eslint instead: remove biome from the"
              + " project, then run init again.");
    }
  }

  /** Returns the source-language gates of a Node project, by its language. */
  public static SourceLanguages<NodeTools> languages(
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
