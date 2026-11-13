package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFilePlans;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.project.node.FileSystemNodeToolConfigs;
import dev.ward.infrastructure.project.node.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The test runner configs hold reporters, never a threshold, and earlier ones are cleaned. */
class NodeTestConfigFilesTest {
  private static final String VITEST_CONFIG = "vitest.config.mjs";
  private static final String JEST_CONFIG = "jest.config.mjs";
  private static final String VITEST = "vitest";
  private static final String JEST = "jest";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(
          RuleFileTargetsFixtures.of(
              new FileSystemPackageManifests(), new FileSystemNodeToolConfigs()));

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void vitestConfigWritesTheReportersWithoutAThresholdAndRollbackRemovesIt() throws IOException {
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates(VITEST));

    assertThat(Files.readString(root.resolve(VITEST_CONFIG)))
        .isEqualTo(
            """
            // managed-by: ward
            import { defineConfig } from "vitest/config";

            export default defineConfig({
              test: {
                coverage: {
                  provider: "v8",
                  reporter: ["text", "lcov"],
                },
              },
            });
            """);
    writer.rollback(change);
    assertThat(root.resolve(VITEST_CONFIG)).doesNotExist();
  }

  @Test
  void everyPresetWritesTheSameVitestConfig() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates(VITEST));
    final String strict = Files.readString(root.resolve(VITEST_CONFIG));
    writer.apply(project(BuildTool.NPM), null, gates(VITEST));

    assertThat(Files.readString(root.resolve(VITEST_CONFIG))).isEqualTo(strict);
    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates(VITEST))))
        .containsExactly(
            new PlannedFileChange(root.resolve(VITEST_CONFIG), PlannedFileChange.Action.UNCHANGED));
  }

  @Test
  void projectsViteConfigIsKeptAndListedAsUserOwned() throws IOException {
    Files.writeString(root.resolve("vite.config.ts"), "export default {};\n");

    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates(VITEST)));
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates(VITEST));

    assertThat(plan)
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(
            tuple(root.resolve("vite.config.ts"), PlannedFileChange.Action.PRESERVE_USER_OWNED));
    assertThat(change.preserved()).containsExactly(root.resolve("vite.config.ts"));
    assertThat(root.resolve(VITEST_CONFIG)).doesNotExist();
  }

  @Test
  void jestConfiguredInPackageJsonGetsNoConfigFile() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"jest\": {\"testEnvironment\": \"node\"}}");

    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates(JEST))))
        .isEmpty();
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates(JEST));
    assertThat(root.resolve(JEST_CONFIG)).doesNotExist();
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
        Framework.NONE,
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
