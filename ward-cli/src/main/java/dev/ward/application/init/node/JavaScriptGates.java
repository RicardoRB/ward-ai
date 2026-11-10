package dev.ward.application.init.node;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript's gates on a Node package manager; unlike TypeScript there is no {@code tsc} gate.
 * Ward's JavaScript rules, selected in {@code ward-rules.toml}, do the linting.
 *
 * @param testGate the project's test runner gate, which both presets enable
 */
record JavaScriptGates(String testGate) implements SourceLanguageGates<NodeTools> {
  @Override
  public List<InitGateOption> analysisGates(final NodeTools node) {
    final List<InitGateOption> gates = new ArrayList<>();
    gates.add(node.tests());
    gates.add(node.stryker());
    return List.copyOf(gates);
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    final List<String> gates = new ArrayList<>(List.of(testGate));
    if (preset == QualityPreset.STRICT) {
      gates.add("stryker");
    }
    return List.copyOf(gates);
  }
}
