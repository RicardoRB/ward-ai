package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Stryker config init writes, whose break threshold follows the preset. */
class NodeStrykerConfigTest {
  private static final String CONFIG = "stryker.config.mjs";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(new FileSystemPackageManifests(), new FileSystemNodeToolConfigs());

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void strictVitestConfigBreaksBelowSixtyAndRollbackRemovesIt() throws IOException {
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest", "stryker"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .isEqualTo(
            """
            // managed-by: ward preset=strict
            export default {
              testRunner: "vitest",
              reporters: ["json", "clear-text", "progress"],
              thresholds: {
                high: 80,
                low: 60,
                break: 60,
              },
            };
            """);
    writer.rollback(change);
    assertThat(root.resolve(CONFIG)).doesNotExist();
  }

  @Test
  void standardConfigSetsNoBreakThreshold() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("jest", "stryker"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .contains("testRunner: \"jest\",", "low: 60,\n  },")
        .doesNotContain("break");
  }

  @Test
  void bunUsesTheCommandRunner() throws IOException {
    writer.apply(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test", "stryker"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .isEqualTo(
            """
            // managed-by: ward preset=strict
            export default {
              testRunner: "command",
              commandRunner: {
                command: "bun test",
              },
              coverageAnalysis: "off",
              reporters: ["json", "clear-text", "progress"],
              thresholds: {
                high: 80,
                low: 60,
                break: 60,
              },
            };
            """);
  }

  @Test
  void frameworkProjectMutatesScriptFilesOnly() throws IOException {
    writer.apply(
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.VUE,
            false,
            false,
            root,
            Set.of(),
            "24"),
        QualityPreset.STRICT,
        gates("vitest", "stryker"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .contains("mutate: [\"src/**/*.{js,ts,jsx,tsx,mjs,mts,cts,cjs}\"],")
        .doesNotContain(".vue");
  }

  @Test
  void plainProjectSetsNoMutateGlob() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest", "stryker"));

    assertThat(Files.readString(root.resolve(CONFIG))).doesNotContain("mutate");
  }

  @Test
  void projectsStrykerConfigIsKeptAndListedAsUserOwned() throws IOException {
    Files.writeString(root.resolve("stryker.conf.json"), "{\"testRunner\": \"vitest\"}\n");

    final List<PlannedFileChange> plan =
        writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates("stryker"));
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("stryker"));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(
                root.resolve("stryker.conf.json"), PlannedFileChange.Action.PRESERVE_USER_OWNED));
    assertThat(root.resolve(CONFIG)).doesNotExist();
    assertThat(Files.readString(root.resolve("stryker.conf.json")))
        .isEqualTo("{\"testRunner\": \"vitest\"}\n");
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root, Language.TYPESCRIPT, manager, Framework.NONE, false, false, root, Set.of(), "24");
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
