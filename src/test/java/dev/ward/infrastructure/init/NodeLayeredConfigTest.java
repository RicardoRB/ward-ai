package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
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

/**
 * The layers strict writes over a project's own lint config, and what a re-run or rollback does.
 */
class NodeLayeredConfigTest {
  private static final String CONFIG = "eslint.config.mjs";
  private static final String BASE = "eslint.base.mjs";
  private static final String ESLINT = "eslint";
  private static final String USER_ESLINT =
      "export default [{ rules: { \"no-console\": \"off\" } }];\n";
  private static final String MARKER = "// managed-by: ward preset=strict\n";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(new FileSystemPackageManifests(), new FileSystemNodeToolConfigs());

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void layersAFlatConfigUnderItsBaseFile() throws IOException {
    Files.writeString(root.resolve(CONFIG), USER_ESLINT);

    final List<PlannedFileChange> plan =
        writer.plan(typeScript(), QualityPreset.STRICT, gates(ESLINT));
    final RuleFileChange change = writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(
                root.resolve(BASE),
                PlannedFileChange.Action.CREATE,
                List.of("moved from eslint.config.mjs")),
            new PlannedFileChange(root.resolve(CONFIG), PlannedFileChange.Action.MODIFY));
    assertThat(Files.readString(root.resolve(BASE))).isEqualTo(USER_ESLINT);
    assertThat(Files.readString(root.resolve(CONFIG)))
        .startsWith(MARKER)
        .contains("import base from \"./eslint.base.mjs\";", "await base,")
        .contains("tseslint.configs.strictTypeChecked");
    assertThat(change.preserved()).containsExactly(root.resolve(BASE));
  }

  @Test
  void aTypeScriptConfigKeepsItsExtension() throws IOException {
    Files.writeString(root.resolve("eslint.config.ts"), USER_ESLINT);

    writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(Files.readString(root.resolve("eslint.base.ts"))).isEqualTo(USER_ESLINT);
    assertThat(Files.readString(root.resolve("eslint.config.ts")))
        .startsWith(MARKER)
        .contains("import base from \"./eslint.base.ts\";");
  }

  @Test
  void aCommonJsConfigGetsAnEsmRootAndNoCjsRemains() throws IOException {
    Files.writeString(root.resolve("eslint.config.cjs"), "module.exports = [];\n");

    writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(root.resolve("eslint.base.cjs")).hasContent("module.exports = [];\n");
    assertThat(Files.readString(root.resolve(CONFIG)))
        .contains("import base from \"./eslint.base.cjs\";");
    assertThat(root.resolve("eslint.config.cjs")).doesNotExist();
  }

  @Test
  void aJavaScriptConfigWithoutModuleGetsAnMjsRoot() throws IOException {
    Files.writeString(root.resolve("eslint.config.js"), USER_ESLINT);

    writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(root.resolve("eslint.base.js")).hasContent(USER_ESLINT);
    assertThat(root.resolve(CONFIG)).exists();
    assertThat(root.resolve("eslint.config.js")).doesNotExist();
  }

  @Test
  void aRollbackRestoresTheMovedConfigAndRemovesTheBase() throws IOException {
    Files.writeString(root.resolve("eslint.config.cjs"), "module.exports = [];\n");
    final RuleFileChange change = writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    writer.rollback(change);

    assertThat(root.resolve("eslint.config.cjs")).hasContent("module.exports = [];\n");
    assertThat(root.resolve("eslint.base.cjs")).doesNotExist();
    assertThat(root.resolve(CONFIG)).doesNotExist();
  }

  @Test
  void aRerunRegeneratesTheLayerAndLeavesTheBaseAlone() throws IOException {
    Files.writeString(root.resolve(CONFIG), USER_ESLINT);
    writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));
    final String layer = Files.readString(root.resolve(CONFIG));

    final List<PlannedFileChange> plan =
        writer.plan(typeScript(), QualityPreset.STRICT, gates(ESLINT));
    writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve(CONFIG), PlannedFileChange.Action.UNCHANGED));
    assertThat(root.resolve(BASE)).hasContent(USER_ESLINT);
    assertThat(Files.readString(root.resolve(CONFIG))).isEqualTo(layer);
  }

  @Test
  void aTakenBaseNameIsAConflictThatChangesNothing() throws IOException {
    Files.writeString(root.resolve(CONFIG), USER_ESLINT);
    Files.writeString(root.resolve(BASE), "owned by the user\n");

    final List<PlannedFileChange> plan =
        writer.plan(typeScript(), QualityPreset.STRICT, gates(ESLINT));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(
                root.resolve(BASE),
                PlannedFileChange.Action.CONFLICT,
                List.of("eslint.base.mjs already exists, and Ward did not write it")));
    assertThatThrownBy(() -> writer.apply(typeScript(), QualityPreset.STRICT, gates(ESLINT)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("eslint.base.mjs already exists");
    assertThat(root.resolve(CONFIG)).hasContent(USER_ESLINT);
    assertThat(root.resolve(BASE)).hasContent("owned by the user\n");
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
        TaskRunner.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
