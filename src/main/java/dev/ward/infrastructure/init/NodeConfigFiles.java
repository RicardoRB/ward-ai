package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tool configs init writes in a Node project root: a flat ESLint config, the test runner's
 * coverage thresholds and Stryker's config. A project's own config is reused instead.
 */
final class NodeConfigFiles {
  static final String JS_MARKER = "// managed-by: ward";
  static final String TOML_MARKER = "# managed-by: ward";
  static final String BUNFIG = "bunfig.toml";
  static final String STRYKER = "stryker";
  private static final String ESLINT = "eslint";
  private static final String BIOME = "biome";
  private static final String VITEST = "vitest";
  private static final String JEST = "jest";
  private static final String BUN_TEST = "bun-test";
  private static final String PACKAGE_JSON = "package.json";

  private NodeConfigFiles() {}

  /**
   * Returns each config file the gates need with its marked content; a config the project keeps
   * itself is returned with {@code null} content, meaning it is kept as the user wrote it.
   */
  static Map<Path, String> targets(
      final Project project,
      final PackageManifest manifest,
      final NodeToolConfigs configs,
      final QualityPreset preset,
      final List<InitGateOption> gates) {
    final Path root = project.root();
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final Map<Path, String> targets = new LinkedHashMap<>();
    if (ids.contains(ESLINT)) {
      final String file = manifest.module() ? "eslint.config.js" : "eslint.config.mjs";
      target(
          targets,
          root,
          configs.userConfig(root, ESLINT),
          file,
          marker(JS_MARKER, preset)
              + eslintConfig(project.language(), manifest, preset, ids.contains(JEST)));
    }
    if (ids.contains(VITEST)) {
      target(
          targets,
          root,
          configs.userConfig(root, VITEST),
          "vitest.config.mjs",
          marker(JS_MARKER, preset) + NodeConfigTemplates.vitest(preset));
    }
    if (ids.contains(JEST)) {
      target(
          targets,
          root,
          configs.userConfig(root, JEST),
          "jest.config.mjs",
          marker(JS_MARKER, preset) + NodeConfigTemplates.jest(preset));
    }
    if (ids.contains(BUN_TEST)) {
      targets.put(
          root.resolve(BUNFIG), marker(TOML_MARKER, preset) + NodeConfigTemplates.bunfig(preset));
    }
    if (ids.contains(STRYKER)) {
      target(
          targets,
          root,
          configs.userConfig(root, STRYKER),
          "stryker.config.mjs",
          marker(JS_MARKER, preset) + NodeConfigTemplates.stryker(testGate(ids), preset));
    }
    if (ids.contains(BIOME)) {
      target(
          targets,
          root,
          configs.userConfig(root, BIOME),
          "biome.jsonc",
          marker(JS_MARKER, preset) + NodeConfigTemplates.biome(preset));
    }
    return targets;
  }

  /** Returns whether {@code existing} starts with a Ward marker of a Node config file. */
  static boolean owned(final String existing) {
    return existing.startsWith(JS_MARKER) || existing.startsWith(TOML_MARKER);
  }

  /**
   * Adds the user's config with {@code null} content, or Ward's {@code file}; a config kept in
   * {@code package.json} is already part of the plan, so it adds nothing.
   */
  private static void target(
      final Map<Path, String> targets,
      final Path root,
      final Optional<Path> userConfig,
      final String file,
      final String content) {
    if (userConfig.isEmpty()) {
      targets.put(root.resolve(file), content);
    } else if (!userConfig.get().equals(root.resolve(PACKAGE_JSON))) {
      targets.put(userConfig.get(), null);
    }
  }

  private static String eslintConfig(
      final Language language,
      final PackageManifest manifest,
      final QualityPreset preset,
      final boolean jest) {
    if (language == Language.JAVASCRIPT) {
      return NodeConfigTemplates.javascriptEslint(manifest.module(), jest);
    }
    return preset == QualityPreset.STRICT
        ? NodeConfigTemplates.STRICT_TYPESCRIPT_ESLINT
        : NodeConfigTemplates.TYPESCRIPT_ESLINT;
  }

  private static String testGate(final List<String> ids) {
    return ids.contains(JEST) ? JEST : ids.contains(BUN_TEST) ? BUN_TEST : VITEST;
  }

  private static String marker(final String marker, final QualityPreset preset) {
    return marker + (preset == null ? "" : " preset=" + preset.id()) + "\n";
  }
}
