package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Biome replaces Prettier and ESLint in a Node project's gates, and keeps a project's own ESLint.
 */
class BiomeGatesTest {
  private static final String BIOME = "biome";
  private static final PackageManifest NO_PACKAGES = PackageManifest.EMPTY;
  private static final PackageManifest BIOME_PACKAGE =
      new PackageManifest(Map.of("@biomejs/biome", "2.5.15", "vitest", "^4.0.0"), null, null);
  private static final NodeToolConfigs NO_CONFIGS = (root, gate) -> Optional.empty();
  private static final NodeToolConfigs BIOME_CONFIG =
      (root, gate) ->
          BIOME.equals(gate) ? Optional.of(root.resolve("biome.json")) : Optional.empty();

  @TempDir Path root;

  @Test
  void declaredBiomeReplacesPrettierAndEslint() {
    final InitGeneralCatalog catalog = catalog(BIOME_PACKAGE, NO_CONFIGS, "npm");
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);

    assertThat(catalog.presetGates(project, QualityPreset.STANDARD))
        .containsExactly("tsc", "vitest", BIOME);
    assertThat(ids(catalog, project, null)).contains(BIOME).doesNotContain("prettier", "eslint");
  }

  @Test
  void userBiomeConfigDetectsBiomeWithoutADependency() {
    final InitGeneralCatalog catalog = catalog(NO_PACKAGES, BIOME_CONFIG, "npm");

    assertThat(
            catalog.presetGates(
                project(Language.TYPESCRIPT, BuildTool.NPM), QualityPreset.STANDARD))
        .containsExactly("tsc", "vitest", BIOME);
  }

  @Test
  void noBiomeKeepsPrettierAndEslint() {
    final InitGeneralCatalog catalog = catalog(NO_PACKAGES, NO_CONFIGS, "npm");

    assertThat(
            catalog.presetGates(
                project(Language.TYPESCRIPT, BuildTool.NPM), QualityPreset.STANDARD))
        .containsExactly("tsc", "vitest", "prettier", "eslint");
  }

  @Test
  void strictJavaScriptKeepsItsOwnEslintBesideBiome() {
    final NodeToolConfigs configs =
        (projectRoot, gate) ->
            "eslint".equals(gate)
                ? Optional.of(projectRoot.resolve("eslint.config.mjs"))
                : BIOME_CONFIG.userConfig(projectRoot, gate);
    final InitGeneralCatalog catalog = catalog(BIOME_PACKAGE, configs, "npm");
    final Project project = project(Language.JAVASCRIPT, BuildTool.NPM);

    assertThat(catalog.presetGates(project, QualityPreset.STRICT))
        .containsExactly("vitest", BIOME, "eslint", "stryker", "dependency-cruiser");
    assertThat(ids(catalog, project, QualityPreset.STRICT))
        .contains(BIOME, "eslint")
        .doesNotContain("prettier");
  }

  @ParameterizedTest
  @CsvSource({
    "NPM, npx biome ci --colors=off --reporter=checkstyle .",
    "PNPM, pnpm exec biome ci --colors=off --reporter=checkstyle .",
    "YARN, yarn biome ci --colors=off --reporter=checkstyle .",
    "BUN, bunx biome ci --colors=off --reporter=checkstyle ."
  })
  void biomeCommandRunsThroughThePackageManager(final BuildTool manager, final String command) {
    final InitGeneralCatalog catalog = catalog(BIOME_PACKAGE, NO_CONFIGS, "npm", "pnpm", "yarn");

    assertThat(
            gate(catalog, project(Language.TYPESCRIPT, manager), QualityPreset.STANDARD, BIOME)
                .command())
        .isEqualTo(List.of(command.split(" ")));
  }

  @Test
  void strictBiomeCommandFailsOnWarnings() {
    final InitGeneralCatalog catalog = catalog(BIOME_PACKAGE, NO_CONFIGS, "npm");

    assertThat(
            gate(catalog, project(Language.TYPESCRIPT, BuildTool.NPM), QualityPreset.STRICT, BIOME)
                .command())
        .containsExactly(
            "npx",
            BIOME,
            "ci",
            "--colors=off",
            "--reporter=checkstyle",
            "--error-on-warnings",
            ".");
  }

  private InitGeneralCatalog catalog(
      final PackageManifest manifest, final NodeToolConfigs configs, final String... executables) {
    return new InitGeneralCatalog(
        InitInputs.of(
            new FakeEnvironmentInspection(executables),
            new NodeInputs(directory -> manifest, directory -> false, configs)));
  }

  private Project project(final Language language, final BuildTool manager) {
    return new Project(
        root,
        language,
        manager,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }

  private static List<String> ids(
      final InitGeneralCatalog catalog, final Project project, final QualityPreset preset) {
    return catalog.gates(project, preset).stream().map(InitGateOption::id).toList();
  }

  private static InitGateOption gate(
      final InitGeneralCatalog catalog,
      final Project project,
      final QualityPreset preset,
      final String id) {
    return catalog.gates(project, preset).stream()
        .filter(gate -> gate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
