package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

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

/** The ESLint config the strict preset writes, and the configs it leaves alone. */
class NodeStrictEslintConfigTest {
  private static final String CONFIG = "eslint.config.mjs";
  private static final String USER_ESLINT = "export default [];\n";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(new FileSystemPackageManifests(), new FileSystemNodeToolConfigs());

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"shop\"\n}\n");
  }

  @Test
  void strictTypeScriptConfigUsesTypeCheckedRulesOutsideJavaScriptFiles() throws IOException {
    writer.apply(project(Language.TYPESCRIPT), QualityPreset.STRICT, gates("eslint"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .isEqualTo(
            """
            // managed-by: ward preset=strict
            import js from "@eslint/js";
            import { defineConfig } from "eslint/config";
            import tseslint from "typescript-eslint";

            export default defineConfig(
              { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
              js.configs.recommended,
              tseslint.configs.strictTypeChecked,
              {
                languageOptions: {
                  parserOptions: {
                    projectService: true,
                    tsconfigRootDir: import.meta.dirname,
                  },
                },
              },
              {
                rules: {
                  "@typescript-eslint/no-extraneous-class": [
                    "error",
                    { allowWithDecorator: true },
                  ],
                },
              },
              {
                files: ["**/*.{js,mjs,cjs}"],
                extends: [tseslint.configs.disableTypeChecked],
              },
            );
            """);
  }

  @Test
  void strictJavaScriptConfigKeepsTheRecommendedRules() throws IOException {
    writer.apply(project(Language.JAVASCRIPT), QualityPreset.STANDARD, gates("eslint"));
    final String standard = Files.readString(root.resolve(CONFIG));

    writer.apply(project(Language.JAVASCRIPT), QualityPreset.STRICT, gates("eslint"));

    assertThat(Files.readString(root.resolve(CONFIG)))
        .isEqualTo(standard.replace("preset=standard", "preset=strict"))
        .doesNotContain("typescript-eslint");
  }

  @Test
  void strictLayersTheProjectsOwnFlatConfig() throws IOException {
    Files.writeString(root.resolve("eslint.config.js"), "export default [];\n");

    final RuleFileChange change =
        writer.apply(project(Language.TYPESCRIPT), QualityPreset.STRICT, gates("eslint"));

    assertThat(change.preserved()).containsExactly(root.resolve("eslint.base.js"));
    assertThat(Files.readString(root.resolve("eslint.base.js"))).isEqualTo("export default [];\n");
    assertThat(root.resolve("eslint.config.js")).doesNotExist();
    assertThat(Files.readString(root.resolve(CONFIG)))
        .startsWith("// managed-by: ward preset=strict\n")
        .contains("import base from \"./eslint.base.js\";", "await base,");
  }

  @Test
  void strictKeepsAJavaScriptConfigWithAWarning() throws IOException {
    Files.writeString(root.resolve("eslint.config.mjs"), USER_ESLINT);

    final RuleFileChange change =
        writer.apply(
            new Project(
                root,
                Language.JAVASCRIPT,
                BuildTool.NPM,
                Framework.NONE,
                false,
                false,
                root,
                Set.of(),
                "24"),
            QualityPreset.STRICT,
            gates("eslint"));

    assertThat(change.warnings())
        .containsExactly("the strict preset's ESLint rules are not applied to eslint.config.mjs");
    assertThat(root.resolve("eslint.config.mjs")).hasContent(USER_ESLINT);
    assertThat(root.resolve("eslint.base.mjs")).doesNotExist();
  }

  @Test
  void theLayerAndTheGeneratedStrictConfigShareTheStrictRules() {
    assertThat(NodeLayerTemplates.eslint(QualityPreset.STRICT, "eslint.base.mjs", Framework.NONE))
        .contains(NodeConfigTemplates.STRICT_TYPESCRIPT_RULES);
    assertThat(NodeConfigTemplates.STRICT_TYPESCRIPT_ESLINT)
        .contains(NodeConfigTemplates.STRICT_TYPESCRIPT_RULES);
  }

  private Project project(final Language language) {
    return new Project(
        root, language, BuildTool.NPM, Framework.NONE, false, false, root, Set.of(), "24");
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
