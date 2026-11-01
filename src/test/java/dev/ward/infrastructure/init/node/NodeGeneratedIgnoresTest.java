package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The generated ESLint ignores each framework's output folder, and nothing else changes. */
class NodeGeneratedIgnoresTest {
  @Test
  void astroIgnoresItsGeneratedTypes() {
    assertThat(config(QualityPreset.STRICT, Framework.ASTRO))
        .contains("\"build/\",\n      \".astro/\",\n      \".ward/\",\n    ],");
  }

  @Test
  void svelteIgnoresItsGeneratedOutput() {
    assertThat(config(QualityPreset.STRICT, Framework.SVELTE))
        .contains("\"build/\",\n      \".svelte-kit/\",\n      \".ward/\",\n    ],")
        .doesNotContain(".astro/");
  }

  @ParameterizedTest
  @EnumSource(
      value = QualityPreset.class,
      names = {"STANDARD", "STRICT"})
  void frameworksWithoutGeneratedFolderKeepTheConfig(final QualityPreset preset) {
    assertThat(config(preset, Framework.NONE)).doesNotContain(".astro/", ".svelte-kit/");
    assertThat(config(preset, Framework.VUE)).isEqualTo(config(preset, Framework.NONE));
  }

  private static String config(final QualityPreset preset, final Framework framework) {
    return NodeConfigFiles.eslintConfig(
        Language.TYPESCRIPT, PackageManifest.EMPTY, preset, false, framework);
  }
}
