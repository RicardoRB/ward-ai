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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The edit hook's check of a TypeScript project runs only ESLint and Prettier, on the file. */
class TypeScriptPartialCheckTest {
  @TempDir Path root;

  @Test
  void editedFileNarrowsEslintAndPrettierAndSkipsTheRest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"name\": \"shop\"}\n");
    Files.writeString(root.resolve("tsconfig.json"), "{}\n");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.ts"), "export const a = 1;\n");
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24");
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"),
                directory -> PackageManifest.EMPTY,
                directory -> false));
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> gates =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    Files.writeString(
        root.resolve(WardConfiguration.FILE_NAME),
        ConfigurationTemplate.render(gates, QualityPreset.STANDARD, project));
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "[]", "");

    CheckServiceFixtures.checks(executor)
        .checkPartial(root, List.of(root.resolve("src/app.ts")), SupportedQualityGates.specIds());

    assertThat(executor.commands)
        .containsExactlyInAnyOrder(
            List.of("npx", "prettier", "--check", "src/app.ts"),
            List.of("npx", "eslint", "--format", "json", "src/app.ts"));
  }
}
