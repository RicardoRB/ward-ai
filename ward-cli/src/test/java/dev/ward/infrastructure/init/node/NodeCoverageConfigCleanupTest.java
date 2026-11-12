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

/** Coverage configs that earlier versions wrote are cleaned up; the project's own are kept. */
class NodeCoverageConfigCleanupTest {
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
  void vitestConfigFromAnEarlierVersionLosesItsThreshold() throws IOException {
    Files.writeString(
        root.resolve(VITEST_CONFIG),
        "// managed-by: ward preset=standard\nexport default { thresholds: { lines: 70 } };\n");

    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(
            writer.plan(project(BuildTool.NPM), QualityPreset.STANDARD, gates(VITEST)));
    writer.apply(project(BuildTool.NPM), QualityPreset.STANDARD, gates(VITEST));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve(VITEST_CONFIG), PlannedFileChange.Action.MODIFY));
    assertThat(Files.readString(root.resolve(VITEST_CONFIG))).doesNotContain("thresholds");
  }

  @Test
  void jestWritesNoConfigAndRemovesOneFromAnEarlierVersion() throws IOException {
    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates(JEST));
    assertThat(root.resolve(JEST_CONFIG)).doesNotExist();

    Files.writeString(
        root.resolve(JEST_CONFIG),
        "// managed-by: ward preset=strict\nexport default { coverageThreshold: {} };\n");
    final List<PlannedFileChange> plan =
        RuleFilePlans.tools(writer.plan(project(BuildTool.NPM), QualityPreset.STRICT, gates(JEST)));
    final RuleFileChange change =
        writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates(JEST));

    assertThat(plan)
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(tuple(root.resolve(JEST_CONFIG), PlannedFileChange.Action.REMOVE));
    assertThat(root.resolve(JEST_CONFIG)).doesNotExist();
    writer.rollback(change);
    assertThat(root.resolve(JEST_CONFIG)).exists();
  }

  @Test
  void jestConfigTheProjectOwnsIsNeverRemoved() throws IOException {
    Files.writeString(root.resolve(JEST_CONFIG), "export default {};\n");

    writer.apply(project(BuildTool.NPM), QualityPreset.STRICT, gates(JEST));

    assertThat(Files.readString(root.resolve(JEST_CONFIG))).isEqualTo("export default {};\n");
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
