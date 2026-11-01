package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.QualityPreset;

/**
 * Angular's ESLint layer over a generated TypeScript config: angular-eslint's TypeScript rules and
 * its template rules, so {@code .html} templates are linted.
 */
public final class NodeAngularEslintTemplate {
  private static final String TYPESCRIPT_ESLINT_IMPORT =
      "import tseslint from \"typescript-eslint\";\n";
  private static final String FLAT_CONFIG_END = "\n);\n";

  // The processor extracts inline templates from component classes.
  private static final String ANGULAR_ESLINT_IMPORT = "import angular from \"angular-eslint\";\n";
  private static final String ANGULAR_ESLINT_BLOCKS =
      """
        {
          files: ["**/*.ts"],
          extends: [angular.configs.tsRecommended],
          processor: angular.processInlineTemplates,
        },
        {
          files: ["**/*.html"],
          extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
        },
      """;

  // The typed strict rules apply to script files only: they need type information, which templates
  // lack, and the parser they set would replace the template parser on .html files.
  private static final String STANDARD_LAYER_RULES =
      """
        {
          files: ["**/*.{ts,mts,cts,js,mjs,cjs}"],
          extends: [tseslint.configs.recommended],
        },
      """;
  private static final String STRICT_LAYER_RULES =
      """
        {
          files: ["**/*.{ts,mts,cts,js,mjs,cjs}"],
          extends: [tseslint.configs.strictTypeChecked],
          languageOptions: {
            parserOptions: {
              projectService: true,
              tsconfigRootDir: import.meta.dirname,
            },
          },
          rules: {
            "@typescript-eslint/no-extraneous-class": ["error", { allowWithDecorator: true }],
          },
        },
        {
          files: ["**/*.{js,mjs,cjs}"],
          extends: [tseslint.configs.disableTypeChecked],
        },
      """;
  // Last in the layer, so the template parser and rules win on .html files again.
  private static final String TEMPLATE_LAYER_BLOCK =
      """
        {
          files: ["**/*.html"],
          extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
        },
      """;

  private NodeAngularEslintTemplate() {}

  /**
   * Returns the ESLint layer for an Angular project: {@code layerTemplate} with angular-eslint
   * imported, its TypeScript rules limited to script files, and its template configs last.
   */
  static String layer(final String layerTemplate, final QualityPreset preset, final String base) {
    final String typed = preset == QualityPreset.STRICT ? STRICT_LAYER_RULES : STANDARD_LAYER_RULES;
    final String rules = typed + TEMPLATE_LAYER_BLOCK;
    return layerTemplate
        .replace(TYPESCRIPT_ESLINT_IMPORT, TYPESCRIPT_ESLINT_IMPORT + ANGULAR_ESLINT_IMPORT)
        .replace("{base}", base)
        .replace("{rules}", rules.stripTrailing());
  }

  /** Adds angular-eslint's TypeScript and template configs to a generated TypeScript config. */
  public static String apply(final String typescriptConfig) {
    return typescriptConfig
        .replace(TYPESCRIPT_ESLINT_IMPORT, TYPESCRIPT_ESLINT_IMPORT + ANGULAR_ESLINT_IMPORT)
        .replace(FLAT_CONFIG_END, "\n" + ANGULAR_ESLINT_BLOCKS + FLAT_CONFIG_END);
  }
}
