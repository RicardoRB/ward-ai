package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The bodies of the Node tool configs init writes, without their ownership marker. Objects are
 * fully expanded, so Prettier keeps them as written.
 */
public final class NodeConfigTemplates {
  private static final String LINE = "LINE";
  private static final String BRANCH = "BRANCH";
  private static final int PERCENT = 100;
  public static final String TYPESCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
        js.configs.recommended,
        tseslint.configs.recommended,
      );
      """;

  // Init's own .mjs configs lie outside tsconfig.json, so type-checked rules skip JavaScript files.
  // The layer over a project's own config shares these rules with the generated strict config.
  static final String STRICT_TYPESCRIPT_RULES =
      """
        tseslint.configs.strictTypeChecked,
        {
          languageOptions: {
            parserOptions: {
              projectService: true,
              tsconfigRootDir: import.meta.dirname,
            },
          },
        },
        {
          rules: {
            "@typescript-eslint/no-extraneous-class": [
              "error",
              { allowWithDecorator: true },
            ],
          },
        },
        {
          files: ["**/*.{js,mjs,cjs}"],
          extends: [tseslint.configs.disableTypeChecked],
        },
      """;

  public static final String STRICT_TYPESCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
        js.configs.recommended,
      """
          + STRICT_TYPESCRIPT_RULES
          + ");\n";

  static final String STANDARD_TYPESCRIPT_RULES = "  tseslint.configs.recommended,\n";

  private static final String IGNORES_LINE =
      "  { ignores: [\"dist/\", \"coverage/\", \".next/\", \"out/\", \"build/\", \".ward/\"] },\n";

  // Ward cannot tell Node code from browser code, so both globals sets keep no-undef quiet.
  private static final String JAVASCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import globals from "globals";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
        js.configs.recommended,
        { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
        {
          files: ["**/*.jsx"],
          languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } },
        },
      {jest}{commonjs});
      """;

  // Jest's test, expect and describe are globals in its default test file locations.
  private static final String JEST_BLOCK =
      """
        {
          files: ["**/__tests__/**", "**/*.{test,spec}.{js,jsx,mjs,cjs}"],
          languageOptions: { globals: globals.jest },
        },
      """;

  // ESLint parses .js as ESM and does not read "type" from package.json.
  private static final String COMMONJS_BLOCK =
      "  { files: [\"**/*.js\"], languageOptions: { sourceType: \"commonjs\" } },\n";

  private static final String VITEST =
      """
      import { defineConfig } from "vitest/config";

      export default defineConfig({
        test: {
          coverage: {
            provider: "v8",
            thresholds: {
      {thresholds}
            },
          },
        },
      });
      """;

  private static final String JEST =
      """
      export default {
        coverageThreshold: {
          global: {
      {thresholds}
          },
        },
      };
      """;

  // The bodies biome format writes with spaces, so Biome reports no format finding in them.

  private static final String BIOME_STANDARD =
      """
      {
        "formatter": { "indentStyle": "space" },
        "vcs": { "enabled": true, "clientKind": "git", "useIgnoreFile": true },
        "linter": { "enabled": true, "rules": { "recommended": true } }
      }
      """;

  private static final String BIOME_STRICT =
      """
      {
        "formatter": { "indentStyle": "space" },
        "vcs": { "enabled": true, "clientKind": "git", "useIgnoreFile": true },
        "linter": {
          "enabled": true,
          "rules": {
            "recommended": true,
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

  private NodeConfigTemplates() {}

  /**
   * Returns the JavaScript flat config, parsing {@code .js} as CommonJS unless {@code module}, and
   * declaring Jest's globals in test files when {@code jest}.
   */
  public static String javascriptEslint(final boolean module, final boolean jest) {
    return JAVASCRIPT_ESLINT
        .replace("{jest}", jest ? JEST_BLOCK : "")
        .replace("{commonjs}", module ? "" : COMMONJS_BLOCK);
  }

  /** Returns a Vitest config that fails below {@code preset}'s coverage minimums. */
  public static String vitest(final QualityPreset preset) {
    return VITEST.replace("{thresholds}", properties(thresholds(preset), "        "));
  }

  /** Returns a Jest config that fails below {@code preset}'s global coverage minimums. */
  public static String jest(final QualityPreset preset) {
    return JEST.replace("{thresholds}", properties(thresholds(preset), "      "));
  }

  /** Returns Bun's test config, which measures lines but not branches. */
  public static String bunfig(final QualityPreset preset) {
    return "[test]\ncoverageThreshold = { lines = " + minimums(preset).get(LINE) + " }\n";
  }

  /**
   * Returns Ward's biome.jsonc. Strict adds the rules that its preset needs; the standard body also
   * keeps Biome out of Git-ignored output such as Ward's coverage reports.
   */
  static String biome(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? BIOME_STRICT : BIOME_STANDARD;
  }

  /**
   * Adds the generated output folder of {@code framework} to the TypeScript config's ignores. Astro
   * writes {@code .astro/} type files and SvelteKit writes {@code .svelte-kit/}; ESLint would lint
   * them and report findings Ward did not write. Other frameworks leave the config unchanged.
   */
  public static String withGeneratedIgnores(final String config, final Framework framework) {
    final String folder =
        switch (framework) {
          case ASTRO -> ".astro/";
          case SVELTE -> ".svelte-kit/";
          default -> null;
        };
    if (folder == null) {
      return config;
    }
    final StringBuilder entries = new StringBuilder();
    for (final String ignore :
        List.of("dist/", "coverage/", ".next/", "out/", "build/", folder, ".ward/")) {
      entries.append("      \"").append(ignore).append("\",\n");
    }
    return config.replace(IGNORES_LINE, "  {\n    ignores: [\n" + entries + "    ],\n  },\n");
  }

  /** The preset's line and, when it sets one, branch minimum, as whole percentages. */
  private static Map<String, Integer> thresholds(final QualityPreset preset) {
    final Map<String, Double> minimums = minimums(preset);
    final Map<String, Integer> thresholds = new LinkedHashMap<>();
    thresholds.put("lines", percent(minimums.get(LINE)));
    if (minimums.containsKey(BRANCH)) {
      thresholds.put("branches", percent(minimums.get(BRANCH)));
    }
    return thresholds;
  }

  private static int percent(final double ratio) {
    return (int) Math.round(ratio * PERCENT);
  }

  // A custom selection uses the standard preset's minimums, as the test commands do.
  private static Map<String, Double> minimums(final QualityPreset preset) {
    return (preset == null ? QualityPreset.STANDARD : preset).rules().jacocoMinimums();
  }

  private static String properties(final Map<String, Integer> values, final String indent) {
    return values.entrySet().stream()
        .map(entry -> indent + entry.getKey() + ": " + entry.getValue() + ",")
        .collect(Collectors.joining("\n"));
  }
}
