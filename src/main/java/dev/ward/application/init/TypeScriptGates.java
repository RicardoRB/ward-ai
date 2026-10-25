package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;

/**
 * TypeScript's gates on a Node package manager.
 *
 * @param testGate the project's test runner gate, which both presets enable
 * @param linting the project's lint and format tools
 */
record TypeScriptGates(String testGate, NodeLinting linting)
    implements SourceLanguageGates<NodeTools> {
  @Override
  public List<InitGateOption> analysisGates(final NodeTools node) {
    final List<InitGateOption> gates = new ArrayList<>();
    gates.add(
        node.option(
            "tsc",
            "Type-checks the project with the TypeScript compiler.",
            node.command("tsc", "--noEmit", "--pretty", "false")));
    gates.add(node.tests());
    gates.addAll(
        linting.gates(
            node,
            node.option(
                "eslint",
                "Lints sources with ESLint and typescript-eslint's recommended rules.",
                node.command("eslint", ".", "--format", "json"))));
    gates.add(node.dependencyCruiser());
    gates.add(node.stryker());
    return List.copyOf(gates);
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    final List<String> gates = new ArrayList<>(List.of("tsc", testGate));
    gates.addAll(linting.presetIds());
    if (preset == QualityPreset.STRICT) {
      gates.addAll(List.of("stryker", "dependency-cruiser"));
    }
    return List.copyOf(gates);
  }
}
