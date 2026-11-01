package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init refuses Biome for an Angular project before it plans any change. */
class AngularBiomeRefusalTest {
  @TempDir Path root;

  @Test
  void angularProjectWithBiomeIsRefusedNamingBiomeAndEslint() {
    final Project project = project(Framework.ANGULAR);

    assertThatThrownBy(() -> catalog(biome()).gates(project, QualityPreset.STANDARD))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("biome")
        .hasMessageContaining("eslint");
  }

  @Test
  void angularProjectWithoutBiomeIsNotRefused() {
    final Project project = project(Framework.ANGULAR);

    assertThatCode(() -> catalog(PackageManifest.EMPTY).gates(project, QualityPreset.STANDARD))
        .doesNotThrowAnyException();
  }

  @Test
  void nonAngularProjectWithBiomeIsNotRefused() {
    final Project project = project(Framework.NONE);

    assertThatCode(() -> catalog(biome()).gates(project, QualityPreset.STANDARD))
        .doesNotThrowAnyException();
  }

  private static PackageManifest biome() {
    return new PackageManifest(Map.of("@biomejs/biome", "2.5.15"), null, null);
  }

  private static InitGeneralCatalog catalog(final PackageManifest manifest) {
    return new InitGeneralCatalog(
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm", "yarn"),
            directory -> manifest,
            directory -> false));
  }

  private Project project(final Framework framework) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
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
