package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The type-check gate a framework project offers in place of {@code tsc}. */
class TypeScriptFrameworkGatesTest {
  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "VUE, NPM, npx vue-tsc --build, vue-tsc",
    "SVELTE, PNPM, pnpm exec svelte-check, svelte-check",
    "ASTRO, YARN, yarn astro check, astro-check",
    "ANGULAR, NPM, npx ngc -p tsconfig.app.json --noEmit, ngc",
    "ANGULAR, PNPM, pnpm exec ngc -p tsconfig.app.json --noEmit, ngc",
    "ANGULAR, YARN, yarn ngc -p tsconfig.app.json --noEmit, ngc",
    "NONE, NPM, npx tsc --noEmit --pretty false, tsc"
  })
  void frameworkCheckerReplacesTscInTheTypeCheckGate(
      final Framework framework, final BuildTool manager, final String command, final String id) {
    final Project project = project(manager, framework);

    assertThat(catalog().presetGates(project, QualityPreset.STANDARD)).first().isEqualTo(id);
    assertThat(catalog().gates(project, null))
        .filteredOn(gate -> gate.id().equals(id))
        .singleElement()
        .satisfies(gate -> assertThat(String.join(" ", gate.command())).isEqualTo(command));
  }

  @Test
  void frameworkTypeCheckLeavesNoTscGate() {
    final Project project = project(BuildTool.NPM, Framework.VUE);

    assertThat(catalog().presetGates(project, QualityPreset.STRICT)).doesNotContain("tsc");
    assertThat(catalog().gates(project, null)).extracting(InitGateOption::id).doesNotContain("tsc");
  }

  @Test
  void angularStrictPresetOmitsStrykerAndStillKeepsDependencyCruiser() {
    final Project angular = project(BuildTool.NPM, Framework.ANGULAR);

    assertThat(catalog().presetGates(angular, QualityPreset.STRICT))
        .doesNotContain("stryker")
        .contains("dependency-cruiser");
    assertThat(catalog().gates(angular, null))
        .extracting(InitGateOption::id)
        .doesNotContain("stryker");
    assertThat(catalog().presetGates(project(BuildTool.NPM, Framework.VUE), QualityPreset.STRICT))
        .contains("stryker");
  }

  private InitGeneralCatalog catalog() {
    return new InitGeneralCatalog(
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm", "yarn"),
            directory -> PackageManifest.EMPTY,
            directory -> false));
  }

  private Project project(final BuildTool manager, final Framework framework) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
        framework,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}
