package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tool configs init writes in a Node project root: a flat ESLint config and Bun's coverage
 * threshold. A project's own config is reused instead.
 */
final class NodeConfigFiles {
  static final String JS_MARKER = "// managed-by: ward";
  static final String TOML_MARKER = "# managed-by: ward";
  static final String BUNFIG = "bunfig.toml";
  private static final String TYPESCRIPT_ESLINT_CONFIG =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import tseslint from "typescript-eslint";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".ward/"] },
        js.configs.recommended,
        tseslint.configs.recommended,
      );
      """;

  // Ward cannot tell Node code from browser code, so both globals sets keep no-undef quiet.
  private static final String JAVASCRIPT_ESLINT_CONFIG =
      """
      import js from "@eslint/js";
      import { defineConfig } from "eslint/config";
      import globals from "globals";

      export default defineConfig(
        { ignores: ["dist/", "coverage/", ".ward/"] },
        js.configs.recommended,
        { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
        {
          files: ["**/*.jsx"],
          languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } },
        },
      {commonjs});
      """;

  // ESLint parses .js as ESM and does not read "type" from package.json.
  private static final String COMMONJS_BLOCK =
      "  { files: [\"**/*.js\"], languageOptions: { sourceType: \"commonjs\" } },\n";

  private NodeConfigFiles() {}

  /**
   * Returns each config file the gates need with its marked content; an existing ESLint config is
   * returned with {@code null} content, meaning it is kept as the user wrote it.
   */
  static Map<Path, String> targets(
      final Path root,
      final Language language,
      final PackageManifest manifest,
      final QualityPreset preset,
      final List<InitGateOption> gates)
      throws IOException {
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final Map<Path, String> targets = new LinkedHashMap<>();
    if (ids.contains("eslint")) {
      final Optional<Path> existing = existingEslintConfig(root);
      if (existing.isPresent()) {
        targets.put(existing.get(), null);
      } else {
        targets.put(
            root.resolve(manifest.module() ? "eslint.config.js" : "eslint.config.mjs"),
            marker(JS_MARKER, preset) + eslintConfig(language, manifest));
      }
    }
    if (ids.contains("bun-test")) {
      targets.put(
          root.resolve(BUNFIG),
          marker(TOML_MARKER, preset)
              + "[test]\ncoverageThreshold = { lines = "
              + lineMinimum(preset)
              + " }\n");
    }
    return targets;
  }

  /** Returns whether {@code existing} starts with a Ward marker of a Node config file. */
  static boolean owned(final String existing) {
    return existing.startsWith(JS_MARKER) || existing.startsWith(TOML_MARKER);
  }

  private static String eslintConfig(final Language language, final PackageManifest manifest) {
    if (language != Language.JAVASCRIPT) {
      return TYPESCRIPT_ESLINT_CONFIG;
    }
    return JAVASCRIPT_ESLINT_CONFIG.replace("{commonjs}", manifest.module() ? "" : COMMONJS_BLOCK);
  }

  private static Optional<Path> existingEslintConfig(final Path root) throws IOException {
    try (DirectoryStream<Path> files =
        Files.newDirectoryStream(root, "{eslint.config.*,.eslintrc*}")) {
      for (final Path file : files) {
        if (Files.isRegularFile(file) && !owned(Files.readString(file))) {
          return Optional.of(file);
        }
      }
    }
    return Optional.empty();
  }

  private static String marker(final String marker, final QualityPreset preset) {
    return marker + (preset == null ? "" : " preset=" + preset.id()) + "\n";
  }

  // shortcut: TypeScript has only the standard preset (#84), so custom selections use its minimum.
  private static double lineMinimum(final QualityPreset preset) {
    final QualityPreset source = preset == null ? QualityPreset.STANDARD : preset;
    return source.rules().jacocoMinimums().get("LINE");
  }
}
