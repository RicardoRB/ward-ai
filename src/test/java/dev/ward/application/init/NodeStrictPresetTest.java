package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The Stryker gate and the strict preset of TypeScript and JavaScript projects. */
class NodeStrictPresetTest {
  @TempDir Path root;

  @ParameterizedTest
  @EnumSource(
      value = Language.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void strykerIsOfferedAndRunsThroughThePackageManager(final Language language) {
    assertThat(gate(project(language, BuildTool.PNPM), "stryker").command())
        .containsExactly("pnpm", "exec", "stryker", "run");
    assertThat(gate(project(language, BuildTool.NPM), "stryker").command())
        .containsExactly("npx", "stryker", "run");
  }

  @Test
  void strykerRendersInCiOnlyWithoutAMinimum() throws IOException {
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(
        file,
        ConfigurationTemplate.render(
            List.of(gate(project, "stryker")), QualityPreset.STRICT, project));

    final GateConfiguration stryker =
        new TomlConfigurationReader().read(file).checks().get("stryker");

    assertThat(stryker.profiles()).isEqualTo(Set.of("ci"));
    assertThat(stryker.minimum()).isNull();
    assertThat(Files.readString(file)).doesNotContain("minimum");
  }

  @Test
  void strykerAddsItsOutputToTheGitignoreSection() {
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> true,
                (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    final InitSelection selection =
        new InitSelection(
            List.of("none"),
            List.of(),
            null,
            QualityPreset.STRICT,
            List.of(),
            List.of(),
            List.of(),
            false,
            null,
            false);

    assertThat(
            OwnedSections.of(
                    catalog, project, selection, List.of(gate(project, "stryker")), List.of())
                .getFirst()
                .body())
        .isEqualTo(".ward/reports/\ncoverage/\n.stryker-tmp/\nreports/mutation/\n");
    assertThat(
            OwnedSections.of(
                    catalog, project, selection, List.of(gate(project, "eslint")), List.of())
                .getFirst()
                .body())
        .isEqualTo(".ward/reports/\ncoverage/\n");
  }

  private InitGateOption gate(final Project project, final String id) {
    return catalog().gates(project, null).stream()
        .filter(gate -> gate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static InitGeneralCatalog catalog() {
    return new InitGeneralCatalog(
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm"),
            directory -> PackageManifest.EMPTY,
            directory -> true));
  }

  private Project project(final Language language, final BuildTool manager) {
    return new Project(root, language, manager, Framework.NONE, false, false, root, Set.of(), "24");
  }
}
