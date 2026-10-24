package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * TypeScript's gates on a Node package manager.
 *
 * @param testGate the project's test runner gate, which both presets enable
 */
record TypeScriptGates(String testGate) implements SourceLanguageGates<NodeTools> {
  @Override
  public List<InitGateOption> analysisGates(final NodeTools node) {
    return List.of(
        node.option(
            "tsc",
            "Type-checks the project with the TypeScript compiler.",
            node.command("tsc", "--noEmit", "--pretty", "false")),
        node.tests(),
        node.option(
            "eslint",
            "Lints sources with ESLint and typescript-eslint's recommended rules.",
            node.command("eslint", ".", "--format", "json")),
        node.option(
            "prettier",
            "Checks formatting with Prettier.",
            node.command("prettier", "--check", ".")),
        node.stryker());
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT
        ? List.of("tsc", testGate, "prettier", "eslint", "stryker")
        : List.of("tsc", testGate, "prettier", "eslint");
  }
}
