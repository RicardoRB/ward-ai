package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * JavaScript's gates on a Node package manager; unlike TypeScript there is no {@code tsc} gate.
 *
 * @param testGate the project's test runner gate, which both presets enable
 */
record JavaScriptGates(String testGate) implements SourceLanguageGates<NodeTools> {
  @Override
  public List<InitGateOption> analysisGates(final NodeTools node) {
    return List.of(
        node.tests(),
        node.option(
            "eslint",
            "Lints sources with ESLint's recommended JavaScript rules.",
            node.command("eslint", ".", "--format", "json")),
        node.option(
            "prettier",
            "Checks formatting with Prettier.",
            node.command("prettier", "--check", ".")),
        node.dependencyCruiser(),
        node.stryker());
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT
        ? List.of(testGate, "prettier", "eslint", "stryker", "dependency-cruiser")
        : List.of(testGate, "prettier", "eslint");
  }
}
