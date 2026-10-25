package dev.ward.application.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Init choices for npm, pnpm, Yarn and Bun projects. */
final class NodeInitSupport implements ToolchainInitSupport {

  @Override
  public Gates gates(final Project project, final QualityPreset preset, final InitInputs inputs) {
    final NodeTools tools =
        NodeTools.of(project, inputs.manifests().read(project.root()), inputs, preset);
    return new Gates(
        languages(project, tools.testGate(), tools.linting()).analysisGates(tools), List.of());
  }

  @Override
  public List<String> presetGates(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    final PackageManifest manifest = inputs.manifests().read(project.root());
    final String testGate = NodeTools.testGate(project.buildTool(), manifest);
    final NodeLinting linting = NodeLinting.of(project, manifest, inputs.configs());
    return languages(project, testGate, linting).presetGates(preset);
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
  public String ciSetup(final Project project, final PackageManifest manifest) {
    return NodeCiSetup.steps(project, manifest.packageManager());
  }

  private static SourceLanguages<NodeTools> languages(
      final Project project, final String testGate, final NodeLinting linting) {
    return SourceLanguages.of(
        Map.<Language, SourceLanguageGates<NodeTools>>of(
            Language.TYPESCRIPT,
            new TypeScriptGates(testGate, linting),
            Language.JAVASCRIPT,
            new JavaScriptGates(testGate, linting)),
        project);
  }
}
