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
        writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest"));

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
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));
    final String strict = Files.readString(root.resolve(VITEST_CONFIG));
    writer.apply(project(BuildTool.NPM), null, gates("vitest"));

    assertThat(Files.readString(root.resolve(VITEST_CONFIG))).isEqualTo(strict);
    assertThat(
            RuleFilePlans.tools(
                writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest"))))
        .containsExactly(
            new PlannedFileChange(root.resolve(VITEST_CONFIG), PlannedFileChange.Action.UNCHANGED));
  }

  @Test
  void vitestConfigFromAnEarlierVersionLosesItsThreshold() throws IOException {
    Files.writeString(
        root.resolve(VITEST_CONFIG),
        "// managed-by: ward preset=standard\nexport default { thresholds: { lines: 70 } };\n");

    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest")));
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("vitest"));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve(VITEST_CONFIG), PlannedFileChange.Action.MODIFY));
    assertThat(Files.readString(root.resolve(VITEST_CONFIG))).doesNotContain("thresholds");
  }

  @Test
  void jestWritesNoConfigAndRemovesOneFromAnEarlierVersion() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("jest"));
    assertThat(root.resolve("jest.config.mjs")).doesNotExist();

    Files.writeString(
        root.resolve("jest.config.mjs"),
        "// managed-by: ward preset=strict\nexport default { coverageThreshold: {} };\n");
    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates("jest")));
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("jest"));

    assertThat(plan)
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(tuple(root.resolve("jest.config.mjs"), PlannedFileChange.Action.REMOVE));
    assertThat(root.resolve("jest.config.mjs")).doesNotExist();
    writer.rollback(change);
    assertThat(root.resolve("jest.config.mjs")).exists();
  }

  @Test
  void jestConfigTheProjectOwnsIsNeverRemoved() throws IOException {
    Files.writeString(root.resolve("jest.config.mjs"), "export default {};\n");

    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("jest"));

    assertThat(Files.readString(root.resolve("jest.config.mjs"))).isEqualTo("export default {};\n");
  }

  @Test
  void projectsViteConfigIsKeptAndListedAsUserOwned() throws IOException {
    Files.writeString(root.resolve("vite.config.ts"), "export default {};\n");

    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest")));
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates("vitest"));

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
                writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates("jest"))))
        .isEmpty();
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates("jest"));
    assertThat(root.resolve("jest.config.mjs")).doesNotExist();
  }

  @Test
  void bunWritesNoConfig() throws IOException {
    writer.apply(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test"));

    assertThat(root.resolve("bunfig.toml")).doesNotExist();
  }

  @Test
  void bunfigFromAnEarlierVersionIsRemovedAndANotherOneIsKept() throws IOException {
    Files.writeString(
        root.resolve("bunfig.toml"),
        "# managed-by: ward preset=strict\n[test]\ncoverageThreshold = { lines = 0.85 }\n");
    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test")));
    writer.apply(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test"));

    assertThat(plan)
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(tuple(root.resolve("bunfig.toml"), PlannedFileChange.Action.REMOVE));
    assertThat(root.resolve("bunfig.toml")).doesNotExist();

    Files.writeString(root.resolve("bunfig.toml"), "[test]\ncoverageThreshold = 0.5\n");
    writer.apply(project(BuildTool.BUN), QualityPreset.STRICT, gates("bun-test"));
    assertThat(Files.readString(root.resolve("bunfig.toml")))
        .isEqualTo("[test]\ncoverageThreshold = 0.5\n");
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
