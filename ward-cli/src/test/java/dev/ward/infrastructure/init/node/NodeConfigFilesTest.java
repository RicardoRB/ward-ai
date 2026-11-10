package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.project.node.FileSystemNodeToolConfigs;
import dev.ward.infrastructure.project.node.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ESLint and Bun configs init writes in a Node project, and the user configs it reuses. */
class NodeConfigFilesTest {
  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(
          RuleFileTargetsFixtures.of(
              new FileSystemPackageManifests(), new FileSystemNodeToolConfigs()));

  @Test
  void newBunfigSetsThePresetLineThreshold() throws IOException {
    manifest("{\n  \"name\": \"shop\"\n}\n");

    final RuleFileChange change =
        writer.apply(project(BuildTool.BUN), QualityPreset.STANDARD, gates("bun-test"));

    assertThat(Files.readString(root.resolve("bunfig.toml")))
        .isEqualTo(
            "# managed-by: ward preset=standard\n[test]\ncoverageThreshold = { lines = 0.7 }\n");
    writer.rollback(change);
    assertThat(root.resolve("bunfig.toml")).doesNotExist();
  }

  @Test
  void existingBunfigIsKeptWithAWarning() throws IOException {
    manifest("{\n  \"name\": \"shop\"\n}\n");
    Files.writeString(root.resolve("bunfig.toml"), "[install]\nexact = true\n");

    final RuleFileChange change =
        writer.apply(project(BuildTool.BUN), QualityPreset.STANDARD, gates("bun-test"));
    final List<String> warnings =
        new NodeBuildToolConfigurator(
                new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", ""))
            .apply(project(BuildTool.BUN), gates("bun-test"))
            .warnings();

    assertThat(change.preserved()).containsExactly(root.resolve("bunfig.toml"));
    assertThat(Files.readString(root.resolve("bunfig.toml")))
        .isEqualTo("[install]\nexact = true\n");
    assertThat(warnings).singleElement().asString().contains("bunfig.toml", "coverageThreshold");
  }

  private void manifest(final String content) throws IOException {
    Files.writeString(root.resolve("package.json"), content);
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root,
        Language.TYPESCRIPT,
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

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
