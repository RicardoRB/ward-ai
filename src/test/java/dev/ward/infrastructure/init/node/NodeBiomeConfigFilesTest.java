package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code biome.jsonc} init writes for a Biome project, and the user's config it keeps. */
class NodeBiomeConfigFilesTest {
  private static final String PACKAGE_JSON = "{\n  \"name\": \"shop\"\n}\n";
  private static final String BIOME = "biome";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(
          RuleFileTargetsFixtures.of(
              new FileSystemPackageManifests(), new FileSystemNodeToolConfigs()));

  @Test
  void userBiomeConfigIsKeptAndNoBiomeConfigIsWritten() throws IOException {
    Files.writeString(root.resolve("package.json"), PACKAGE_JSON);
    Files.writeString(root.resolve("biome.json"), "{}\n");

    writer.apply(project(), QualityPreset.STANDARD, gates(BIOME));

    assertThat(root.resolve("biome.jsonc")).doesNotExist();
    assertThat(Files.readString(root.resolve("biome.json"))).isEqualTo("{}\n");
  }

  @Test
  void standardBiomeConfigStartsWithItsMarkerAndEnablesRecommendedRules() throws IOException {
    Files.writeString(root.resolve("package.json"), PACKAGE_JSON);

    writer.apply(project(), QualityPreset.STANDARD, gates(BIOME));

    assertThat(Files.readString(root.resolve("biome.jsonc")))
        .startsWith("// managed-by: ward preset=standard\n")
        .contains("\"recommended\": true", "\"useIgnoreFile\": true")
        .doesNotContain("noExplicitAny");
  }

  @Test
  void strictBiomeConfigSetsTheStrictRulesAndWritesNoEslintConfig() throws IOException {
    Files.writeString(root.resolve("package.json"), PACKAGE_JSON);

    writer.apply(project(), QualityPreset.STRICT, gates(BIOME));

    assertThat(Files.readString(root.resolve("biome.jsonc")))
        .startsWith("// managed-by: ward preset=strict\n")
        .contains("\"noExplicitAny\": \"error\"", "\"noFloatingPromises\": \"error\"");
    assertThat(root.resolve("eslint.config.mjs")).doesNotExist();
    assertThat(root.resolve("eslint.config.js")).doesNotExist();
  }

  private Project project() {
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
