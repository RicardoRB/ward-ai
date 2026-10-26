package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.CheckServiceFixtures;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The edit hook's check of a TypeScript project runs only ESLint and Prettier, on the file. */
class TypeScriptPartialCheckTest {
  @TempDir Path root;

  @Test
  void editedFileNarrowsEslintAndPrettierAndSkipsTheRest() throws IOException {
    final FakeCommandExecutor executor = checkEditedFile(PackageManifest.EMPTY);

    assertThat(executor.commands)
        .containsExactlyInAnyOrder(
            List.of("npx", "prettier", "--check", "src/app.ts"),
            List.of("npx", "eslint", "--format", "json", "src/app.ts"));
  }

  @Test
  void biomeProjectNarrowsBiomeToTheEditedFile() throws IOException {
    final PackageManifest biome =
        new PackageManifest(Map.of("@biomejs/biome", "2.5.15"), null, null);

    final FakeCommandExecutor executor = checkEditedFile(biome);

    assertThat(executor.commands)
        .containsExactly(
            List.of(
                "npx",
                "biome",
                "ci",
                "--colors=off",
                "--reporter=checkstyle",
                "--no-errors-on-unmatched",
                "src/app.ts"));
  }

  @Test
  void vueEditNarrowsEslintToTheComponentFile() throws IOException {
    final PackageManifest vue = new PackageManifest(Map.of("vue", "3.5.0"), null, null);

    final FakeCommandExecutor executor =
        checkEditedFile(vue, Framework.VUE, "src/components/Button.vue");

    assertThat(executor.commands)
        .containsExactly(List.of("npx", "eslint", "--format", "json", "src/components/Button.vue"));
  }

  private FakeCommandExecutor checkEditedFile(final PackageManifest manifest) throws IOException {
    return checkEditedFile(manifest, Framework.NONE, "src/app.ts");
  }

  private FakeCommandExecutor checkEditedFile(
      final PackageManifest manifest, final Framework framework, final String edited)
      throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"name\": \"shop\"}\n");
    Files.writeString(root.resolve("tsconfig.json"), "{}\n");
    Files.createDirectories(root.resolve(edited).getParent());
    Files.writeString(root.resolve(edited), "export const a = 1;\n");
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            framework,
            false,
            false,
            root,
            Set.of(),
            "24");
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"), directory -> manifest, directory -> false));
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> gates =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    Files.writeString(
        root.resolve(WardConfiguration.FILE_NAME),
        ConfigurationTemplate.render(gates, QualityPreset.STANDARD, project));
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "[]", "");

    CheckServiceFixtures.checks(executor)
        .checkPartial(root, List.of(root.resolve(edited)), SupportedQualityGates.specIds());
    return executor;
  }
}
