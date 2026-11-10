package dev.ward.application.init.node;

import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SourceLanguageGates;
import dev.ward.application.init.SourceLanguages;
import dev.ward.application.init.ToolchainInitSupport.Gates;
import dev.ward.application.init.node.turbo.TaskRunnerStrategies;
import dev.ward.application.init.node.turbo.TurborepoPlan;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
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
    final String testGate = TurborepoPlan.testGate(project, inputs);
    final NodeTools tools = NodeTools.of(project, manifest, inputs, preset).withTestGate(testGate);
    return new Gates(
        TurborepoPlan.rootGates(
            languages(project, testGate).analysisGates(tools),
            TaskRunnerStrategies.of(project).orElseThrow(),
            project.buildTool()),
        List.of());
  }

  /** Returns the source-language gates of a Node project, by its language. */
  public static SourceLanguages<NodeTools> languages(final Project project, final String testGate) {
    return SourceLanguages.of(
        Map.<Language, SourceLanguageGates<NodeTools>>of(
            Language.TYPESCRIPT,
            new TypeScriptGates(project.framework(), testGate),
            Language.JAVASCRIPT,
            new JavaScriptGates(testGate)),
        project);
  }
}
