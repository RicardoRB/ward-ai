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

/** The test runner configs that hold the preset's coverage thresholds. */
class NodeTestConfigFilesTest {
  private static final String VITEST_CONFIG = "vitest.config.mjs";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(new FileSystemPackageManifests(), new FileSystemNodeToolConfigs());

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void standardVitestConfigSetsTheLineThresholdAndRollbackRemovesIt() throws IOException {
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest"));

    assertThat(Files.readString(root.resolve(VITEST_CONFIG)))
        .isEqualTo(
            """
            // managed-by: ward preset=standard
            import { defineConfig } from "vitest/config";

            export default defineConfig({
              test: {
                coverage: {
                  provider: "v8",
                  thresholds: {
                    lines: 70,
                  },
                },
              },
            });
            """);
    writer.rollback(change);
    assertThat(root.resolve(VITEST_CONFIG)).doesNotExist();
  }

  @Test
  void strictJestConfigSetsLineAndBranchThresholds() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("jest"));

    assertThat(Files.readString(root.resolve("jest.config.mjs")))
        .isEqualTo(
            """
            // managed-by: ward preset=strict
            export default {
              coverageThreshold: {
                global: {
                  lines: 85,
                  branches: 75,
                },
              },
            };
            """);
  }

  @Test
  void customSelectionUsesTheStandardThresholdWithoutAPresetMarker() throws IOException {
    writer.apply(project(BuildTool.NPM), null, gates("vitest"));

    assertThat(Files.readString(root.resolve(VITEST_CONFIG)))
        .startsWith("// managed-by: ward\n")
        .contains("lines: 70,")
        .doesNotContain("branches");
  }

  @Test
  void presetChangeRewritesWardsConfig() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest"));

    final List<PlannedFileChange> plan =
        writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve(VITEST_CONFIG), PlannedFileChange.Action.MODIFY));
    assertThat(Files.readString(root.resolve(VITEST_CONFIG)))
        .startsWith("// managed-by: ward preset=strict\n")
        .contains("lines: 85,", "branches: 75,");
  }

  @Test
  void projectsViteConfigIsKeptAndListedAsUserOwned() throws IOException {
    Files.writeString(root.resolve("vite.config.ts"), "export default {};\n");

    final List<PlannedFileChange> plan =
        writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(
                root.resolve("vite.config.ts"), PlannedFileChange.Action.PRESERVE_USER_OWNED));
    assertThat(change.preserved()).containsExactly(root.resolve("vite.config.ts"));
    assertThat(root.resolve(VITEST_CONFIG)).doesNotExist();
  }

  @Test
  void jestConfiguredInPackageJsonGetsNoConfigFile() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"jest\": {\"testEnvironment\": \"node\"}}");

    assertThat(writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates("jest")))
        .isEmpty();
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("jest"));
    assertThat(root.resolve("jest.config.mjs")).doesNotExist();
  }

  @Test
  void strictBunfigSetsTheStrictLineThreshold() throws IOException {
    writer.apply(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test"));

    assertThat(Files.readString(root.resolve("bunfig.toml")))
        .isEqualTo(
            "# managed-by: ward preset=strict\n[test]\ncoverageThreshold = { lines = 0.85 }\n");
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
