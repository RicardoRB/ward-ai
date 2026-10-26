package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;

/**
 * The layer bodies init writes over a project's own config. Ward's root config imports or extends
 * its base file and then adds Ward's rules, so Ward's rules win over the base's. Like the other
 * bodies, these omit the ownership marker.
 */
final class NodeLayerTemplates {
  // The root config imports the base config and adds Ward's rules after it, so Ward's win.
  private static final String ESLINT_LAYER =
      """
      import base from "./{base}";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        {
          ignores: [
            "eslint.base.*",
            "eslint.config.*",
            "dist/",
            "coverage/",
            ".next/",
            "out/",
            "build/",
            ".ward/",
          ],
        },
        await base,
      {rules}
      );
      """;

  // Biome's own format, so biome ci reports no problem in the layer file.
  private static final String BIOME_LAYER_STANDARD =
      """
      {
        "extends": ["./{base}"]
      }
      """;

  private static final String BIOME_LAYER_STRICT =
      """
      {
        "extends": ["./{base}"],
        "linter": {
          "rules": {
            "suspicious": { "noExplicitAny": "error" },
            "style": { "noNonNullAssertion": "error", "useConst": "error" },
            "correctness": {
              "noUnusedImports": "error",
              "noUnusedVariables": "error"
            },
            "complexity": { "noExcessiveCognitiveComplexity": "error" },
            "nursery": { "noFloatingPromises": "error", "noMisusedPromises": "error" }
          }
        }
      }
      """;

  private NodeLayerTemplates() {}

  /**
   * Returns the ESLint layer: Ward's rules over the base config in {@code base}. {@code strict}
   * adds the strict TypeScript rules, and standard adds the recommended ones.
   */
  static String eslint(final QualityPreset preset, final String base) {
    final String rules =
        preset == QualityPreset.STRICT
            ? NodeConfigTemplates.STRICT_TYPESCRIPT_RULES
            : NodeConfigTemplates.STANDARD_TYPESCRIPT_RULES;
    return ESLINT_LAYER.replace("{base}", base).replace("{rules}", rules.stripTrailing());
  }

  /**
   * Returns the Biome layer: a root config that extends {@code base}. Strict also sets the eight
   * strict rules here, so they win over the base's settings.
   */
  static String biome(final QualityPreset preset, final String base) {
    final String layer = preset == QualityPreset.STRICT ? BIOME_LAYER_STRICT : BIOME_LAYER_STANDARD;
    return layer.replace("{base}", base);
  }
}
