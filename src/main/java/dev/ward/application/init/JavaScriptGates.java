package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * JavaScript's gates on a Node package manager; unlike TypeScript there is no {@code tsc} gate.
 *
 * @param testGate the project's test runner gate, which the standard preset enables
 */
record JavaScriptGates(String testGate) implements SourceLanguageGates<NodeTools> {
  static final String STRICT_UNAVAILABLE =
      "The strict preset is not available for JavaScript yet; use --preset standard.";

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
            node.command("prettier", "--check", ".")));
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    if (preset == QualityPreset.STRICT) {
      throw new WardException(STRICT_UNAVAILABLE);
    }
    return List.of(testGate, "prettier", "eslint");
  }
}
