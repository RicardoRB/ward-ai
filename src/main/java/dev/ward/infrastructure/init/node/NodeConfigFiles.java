package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigFiles;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.ConfigTarget.Disposition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The tool configs init writes in a Node project root: the ESLint and Biome configs, the test
 * runner's coverage thresholds and Stryker's config. A project's own config is reused instead, or
 * under strict, layered.
 */
final class NodeConfigFiles {
  static final String BUNFIG = "bunfig.toml";
  static final String STRYKER = "stryker";
  static final String ESLINT = "eslint";
  static final String BIOME = "biome";
  private static final String VITEST = "vitest";
  private static final String JEST = "jest";
  private static final String BUN_TEST = "bun-test";
  private static final String PACKAGE_JSON = "package.json";

  private NodeConfigFiles() {}

  /** Returns each config file the gates need, in the order init writes them. */
  static List<ConfigTarget> targets(
      final Project project,
      final PackageManifest manifest,
      final NodeToolConfigs configs,
      final QualityPreset preset,
      final List<InitGateOption> gates)
      throws IOException {
    final Path root = project.root();
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final List<ConfigTarget> targets = new ArrayList<>();
    if (ids.contains(ESLINT)) {
      targets.addAll(
          NodeLintConfigTargets.eslint(project, manifest, configs, preset, ids.contains(JEST)));
    }
    if (ids.contains(VITEST)) {
      target(
          targets,
          root,
          configs.userConfig(root, VITEST),
          "vitest.config.mjs",
          ConfigFiles.marker(ConfigFiles.JS_MARKER, preset) + NodeConfigTemplates.vitest(preset));
    }
    if (ids.contains(JEST)) {
      target(
          targets,
          root,
          configs.userConfig(root, JEST),
          "jest.config.mjs",
          ConfigFiles.marker(ConfigFiles.JS_MARKER, preset) + NodeConfigTemplates.jest(preset));
    }
    if (ids.contains(BUN_TEST)) {
      targets.add(
          new ConfigTarget(
              root.resolve(BUNFIG),
              Disposition.WRITE,
              ConfigFiles.marker(ConfigFiles.TOML_MARKER, preset)
                  + NodeConfigTemplates.bunfig(preset),
              null));
    }
    if (ids.contains(STRYKER)) {
      target(
          targets,
          root,
          configs.userConfig(root, STRYKER),
          "stryker.config.mjs",
          ConfigFiles.marker(ConfigFiles.JS_MARKER, preset)
              + NodeStrykerTemplate.stryker(testGate(ids), preset, project.framework()));
    }
    if (ids.contains(BIOME)) {
      targets.addAll(NodeLintConfigTargets.biome(project, configs, preset));
    }
    return targets;
  }

  /**
   * Adds the user's config as kept, or Ward's {@code file}; a config kept in {@code package.json}
   * is already part of the plan, so it adds nothing.
   */
  private static void target(
      final List<ConfigTarget> targets,
      final Path root,
      final Optional<Path> userConfig,
      final String file,
      final String content) {
    if (userConfig.isEmpty()) {
      targets.add(new ConfigTarget(root.resolve(file), Disposition.WRITE, content, null));
    } else if (!userConfig.get().equals(root.resolve(PACKAGE_JSON))) {
      targets.add(new ConfigTarget(userConfig.get(), Disposition.KEEP, null, null));
    }
  }

  /** Returns Ward's flat ESLint config for {@code language}, without its marker. */
  public static String eslintConfig(
      final Language language,
      final PackageManifest manifest,
      final QualityPreset preset,
      final boolean jest,
      final Framework framework) {
    if (language == Language.JAVASCRIPT) {
      return NodeConfigTemplates.javascriptEslint(manifest.module(), jest);
    }
    final String typescript =
        preset == QualityPreset.STRICT
            ? NodeConfigTemplates.STRICT_TYPESCRIPT_ESLINT
            : NodeConfigTemplates.TYPESCRIPT_ESLINT;
    return NodeConfigTemplates.withGeneratedIgnores(
        framework == Framework.ANGULAR ? NodeAngularEslintTemplate.apply(typescript) : typescript,
        framework);
  }

  private static String testGate(final List<String> ids) {
    return ids.contains(JEST) ? JEST : ids.contains(BUN_TEST) ? BUN_TEST : VITEST;
  }
}
