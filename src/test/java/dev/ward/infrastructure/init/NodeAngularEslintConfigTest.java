package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import org.junit.jupiter.api.Test;

/** The ESLint config Ward writes for an Angular project, which lints templates too. */
class NodeAngularEslintConfigTest {
  @Test
  void standardAngularConfigLintsTemplatesWithAngularEslint() {
    final String config = eslint(QualityPreset.STANDARD, Framework.ANGULAR);

    assertThat(config)
        .contains("import angular from \"angular-eslint\";")
        .contains("angular.configs.tsRecommended")
        .contains("processor: angular.processInlineTemplates")
        .contains("\"**/*.html\"")
        .contains("angular.configs.templateRecommended")
        .contains("angular.configs.templateAccessibility");
  }

  @Test
  void strictAngularConfigKeepsTheStrictRulesAndTemplateLint() {
    final String config = eslint(QualityPreset.STRICT, Framework.ANGULAR);

    assertThat(config)
        .contains("tseslint.configs.strictTypeChecked")
        .contains("angular.configs.templateRecommended");
  }

  @Test
  void plainTypeScriptConfigIsUnchanged() {
    assertThat(eslint(QualityPreset.STANDARD, Framework.NONE))
        .isEqualTo(NodeConfigTemplates.TYPESCRIPT_ESLINT);
    assertThat(eslint(QualityPreset.STRICT, Framework.NONE))
        .isEqualTo(NodeConfigTemplates.STRICT_TYPESCRIPT_ESLINT);
  }

  private static String eslint(final QualityPreset preset, final Framework framework) {
    return NodeConfigFiles.eslintConfig(
        Language.TYPESCRIPT, PackageManifest.EMPTY, preset, false, framework);
  }
}
