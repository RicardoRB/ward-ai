package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ESLint config init writes in a JavaScript project, and the user configs it reuses. */
class JavaScriptEslintConfigTest {
  private static final String ESLINT = "eslint";

  @TempDir Path root;

  private final PresetRuleFileWriter writer =
      new PresetRuleFileWriter(new FileSystemPackageManifests(), new FileSystemNodeToolConfigs());

  @Test
  void commonJsJavaScriptConfigDeclaresGlobalsJsxAndCommonJs() throws IOException {
    manifest("{\n  \"name\": \"shop\"\n}\n");

    final RuleFileChange change =
        writer.apply(javaScript(), QualityPreset.STANDARD, List.of(gate()));

    final Path config = root.resolve("eslint.config.mjs");
    assertThat(Files.readString(config))
        .isEqualTo(
            """
            // managed-by: ward preset=standard
            import js from "@eslint/js";
            import { defineConfig } from "eslint/config";
            import globals from "globals";

            export default defineConfig(
              { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
              js.configs.recommended,
              { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
              {
                files: ["**/*.jsx"],
                languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } },
              },
              { files: ["**/*.js"], languageOptions: { sourceType: "commonjs" } },
            );
            """);
    writer.rollback(change);
    assertThat(config).doesNotExist();
  }

  @Test
  void esmJavaScriptConfigParsesJsAsModules() throws IOException {
    manifest("{\n  \"type\": \"module\"\n}\n");

    writer.apply(javaScript(), QualityPreset.STANDARD, List.of(gate()));

    assertThat(Files.readString(root.resolve("eslint.config.js")))
        .contains("globals.node", "jsx: true")
        .doesNotContain("commonjs", "typescript-eslint")
        .endsWith("  },\n);\n");
  }

  @Test
  void existingJavaScriptConfigsAreReused() throws IOException {
    manifest("{\n  \"type\": \"module\"\n}\n");
    Files.writeString(root.resolve("eslint.config.js"), "export default [];\n");
    Files.writeString(root.resolve(".eslintrc.json"), "{\"root\": true}\n");

    final RuleFileChange change =
        writer.apply(javaScript(), QualityPreset.STANDARD, List.of(gate()));

    assertThat(change.preserved()).hasSize(1);
    assertThat(Files.readString(root.resolve("eslint.config.js")))
        .isEqualTo("export default [];\n");
    assertThat(root.resolve("eslint.config.mjs")).doesNotExist();
  }

  @Test
  void jestProjectGetsJestGlobalsInTestFilesOnly() throws IOException {
    manifest("{\n  \"name\": \"shop\"\n}\n");

    writer.apply(javaScript(), QualityPreset.STANDARD, List.of(gate(), runner("jest")));

    assertThat(Files.readString(root.resolve("eslint.config.mjs")))
        .contains(
            """
              {
                files: ["**/__tests__/**", "**/*.{test,spec}.{js,jsx,mjs,cjs}"],
                languageOptions: { globals: globals.jest },
              },
              { files: ["**/*.js"], languageOptions: { sourceType: "commonjs" } },
            );
            """);
  }

  @Test
  void otherRunnersGetNoJestGlobals() throws IOException {
    manifest("{\n  \"type\": \"module\"\n}\n");

    writer.apply(javaScript(), QualityPreset.STRICT, List.of(gate(), runner("vitest")));

    assertThat(Files.readString(root.resolve("eslint.config.js")))
        .doesNotContain("globals.jest")
        .endsWith("  },\n);\n");
  }

  private Project javaScript() {
    return new Project(
        root,
        Language.JAVASCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE);
  }

  private void manifest(final String content) throws IOException {
    Files.writeString(root.resolve("package.json"), content);
  }

  private static InitGateOption gate() {
    return new InitGateOption(ESLINT, ESLINT, List.of("npx", ESLINT), true, "available");
  }

  private static InitGateOption runner(final String id) {
    return new InitGateOption(id, id, List.of("npx", id), true, "available");
  }
}
