package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The bodies of the Node tool configs init writes, without their ownership marker. Objects are
 * fully expanded, so Prettier keeps them as written.
 */
final class NodeConfigTemplates {
  private static final String LINE = "LINE";
  private static final String BRANCH = "BRANCH";
  private static final int PERCENT = 100;
  static final String TYPESCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".ward/"] },
        js.configs.recommended,
        tseslint.configs.recommended,
      );
      """;

  // Init's own .mjs configs lie outside tsconfig.json, so type-checked rules skip JavaScript files.
  static final String STRICT_TYPESCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".ward/"] },
        js.configs.recommended,
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
          files: ["**/*.{js,mjs,cjs}"],
          extends: [tseslint.configs.disableTypeChecked],
        },
      );
      """;

  // Ward cannot tell Node code from browser code, so both globals sets keep no-undef quiet.
  private static final String JAVASCRIPT_ESLINT =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import globals from "globals";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".ward/"] },
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

  private static final String STRYKER =
      """
      export default {
      {runner}
        reporters: ["json", "clear-text", "progress"],
        thresholds: {
          high: 80,
          low: 60,
      {break}  },
      };
      """;

  // Stryker has no Bun runner plugin; its command runner runs the whole suite per mutant.
  private static final String BUN_RUNNER =
      """
        testRunner: "command",
        commandRunner: {
          command: "bun test",
        },
        coverageAnalysis: "off",
      """
          .stripTrailing();

  private NodeConfigTemplates() {}

  /**
   * Returns the JavaScript flat config, parsing {@code .js} as CommonJS unless {@code module}, and
   * declaring Jest's globals in test files when {@code jest}.
   */
  static String javascriptEslint(final boolean module, final boolean jest) {
    return JAVASCRIPT_ESLINT
        .replace("{jest}", jest ? JEST_BLOCK : "")
        .replace("{commonjs}", module ? "" : COMMONJS_BLOCK);
  }

  /** Returns a Vitest config that fails below {@code preset}'s coverage minimums. */
  static String vitest(final QualityPreset preset) {
    return VITEST.replace("{thresholds}", properties(thresholds(preset), "        "));
  }

  /** Returns a Jest config that fails below {@code preset}'s global coverage minimums. */
  static String jest(final QualityPreset preset) {
    return JEST.replace("{thresholds}", properties(thresholds(preset), "      "));
  }

  /** Returns Bun's test config, which measures lines but not branches. */
  static String bunfig(final QualityPreset preset) {
    return "[test]\ncoverageThreshold = { lines = " + minimums(preset).get(LINE) + " }\n";
  }

  /**
   * Returns a Stryker config for {@code testGate} with the {@code json} reporter Ward reads. Like
   * PIT, it fails below a minimum score only for a preset that sets a mutation threshold.
   */
  static String stryker(final String testGate, final QualityPreset preset) {
    final Integer minimum = preset == null ? null : preset.rules().mutationThreshold();
    final String runner =
        "bun-test".equals(testGate) ? BUN_RUNNER : "  testRunner: \"" + testGate + "\",";
    return STRYKER
        .replace("{runner}", runner)
        .replace("{break}", minimum == null ? "" : "    break: " + minimum + ",\n");
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
