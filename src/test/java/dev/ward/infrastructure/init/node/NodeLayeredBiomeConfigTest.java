package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
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

/** The Biome layers strict writes over a project's own Biome config, and what a re-run does. */
class NodeLayeredBiomeConfigTest {
  private static final String BIOME = "biome";
  private static final String USER_BIOME =
      "{\n  \"linter\": { \"rules\": { \"suspicious\": { \"noExplicitAny\": \"off\" } } }\n}\n";
  private static final String MARKER = "// managed-by: ward preset=strict\n";

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
  void strictLayersAUserBiomeConfigUnderItsBaseFile() throws IOException {
    Files.writeString(root.resolve("biome.json"), USER_BIOME);

    writer.apply(typeScript(), QualityPreset.STRICT, gates(BIOME));

    assertThat(root.resolve("biome.base.json")).hasContent(USER_BIOME);
    assertThat(root.resolve("biome.json")).doesNotExist();
    assertThat(Files.readString(root.resolve("biome.jsonc")))
        .startsWith(MARKER)
        .contains("\"extends\": [\"./biome.base.json\"]", "\"noExplicitAny\": \"error\"");
  }

  @Test
  void aBiomeRerunRegeneratesTheLayerAndLeavesTheBaseAlone() throws IOException {
    Files.writeString(root.resolve("biome.json"), USER_BIOME);
    writer.apply(typeScript(), QualityPreset.STRICT, gates(BIOME));

    final List<PlannedFileChange> plan =
        writer.plan(typeScript(), QualityPreset.STRICT, gates(BIOME));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve("biome.jsonc"), PlannedFileChange.Action.UNCHANGED));
    assertThat(root.resolve("biome.base.json")).hasContent(USER_BIOME);
  }

  @Test
  void standardKeepsAUserBiomeConfigWithAWarning() throws IOException {
    Files.writeString(root.resolve("biome.json"), USER_BIOME);

    final List<PlannedFileChange> plan =
        writer.plan(typeScript(), QualityPreset.STANDARD, gates(BIOME));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(
                root.resolve("biome.json"),
                PlannedFileChange.Action.PRESERVE_USER_OWNED,
                List.of("the standard preset's Biome rules are not applied to biome.json")));
  }

  @Test
  void aDotFileBiomeConfigIsKeptUnderStrictWithAWarning() throws IOException {
    Files.writeString(root.resolve(".biome.json"), USER_BIOME);

    final RuleFileChange change = writer.apply(typeScript(), QualityPreset.STRICT, gates(BIOME));

    assertThat(change.warnings())
        .containsExactly("the strict preset's Biome rules are not applied to .biome.json");
    assertThat(root.resolve("biome.base.json")).doesNotExist();
    assertThat(root.resolve("biome.jsonc")).doesNotExist();
  }

  private Project typeScript() {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
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
