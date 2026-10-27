package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import org.junit.jupiter.api.Test;

/**
 * The layer over an Angular project's own ESLint config. Template linting must survive the
 * layering, so the typed TypeScript rules stay on script files and angular-eslint's template
 * configs come last.
 */
class NodeAngularLayerTest {
  @Test
  void angularLayerImportsAngularEslintAndRestoresTemplateConfigsLast() {
    final String layer =
        NodeLayerTemplates.eslint(QualityPreset.STRICT, "eslint.base.mjs", Framework.ANGULAR);

    assertThat(layer)
        .contains("import angular from \"angular-eslint\";")
        .contains("files: [\"**/*.{ts,mts,cts,js,mjs,cjs}\"]")
        .contains("files: [\"**/*.html\"]")
        .satisfies(
            text ->
                assertThat(text.indexOf("tseslint.configs.strictTypeChecked"))
                    .isLessThan(text.indexOf("\"**/*.html\"")));
  }

  @Test
  void standardAngularLayerScopesRecommendedRulesToScriptFiles() {
    final String layer =
        NodeLayerTemplates.eslint(QualityPreset.STANDARD, "eslint.base.mjs", Framework.ANGULAR);

    assertThat(layer)
        .contains("extends: [tseslint.configs.recommended]")
        .doesNotContain("strictTypeChecked")
        .contains("angular.configs.templateRecommended");
  }

  @Test
  void nonAngularLayerHasNoAngularBlocks() {
    assertThat(NodeLayerTemplates.eslint(QualityPreset.STRICT, "eslint.base.mjs", Framework.VUE))
        .doesNotContain("angular");
  }
}
