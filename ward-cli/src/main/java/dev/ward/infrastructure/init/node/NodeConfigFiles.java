package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.NodeToolConfigs;
import dev.ward.infrastructure.init.ConfigFiles;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.init.ConfigTarget.Disposition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The tool configs init writes in a Node project root: Vitest's coverage reporters and Stryker's
 * config. A project's own config is reused instead, and earlier Ward-written coverage configs that
 * held a threshold are cleaned up.
 */
final class NodeConfigFiles {
  static final String BUNFIG = "bunfig.toml";
  static final String STRYKER = "stryker";
  private static final String VITEST = "vitest";
  private static final String JEST = "jest";
  private static final String BUN_TEST = "bun-test";
  private static final String PACKAGE_JSON = "package.json";

  private NodeConfigFiles() {}

  /** Returns each config file the gates need, in the order init writes them. */
  static List<ConfigTarget> targets(
      final Project project,
      final NodeToolConfigs configs,
      final QualityPreset preset,
      final List<InitGateOption> gates)
      throws IOException {
    final Path root = project.root();
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final List<ConfigTarget> targets = new ArrayList<>();
    if (ids.contains(VITEST)) {
      target(
          targets,
          root,
          configs.userConfig(root, VITEST),
          ConfigTarget.write(
              root.resolve("vitest.config.mjs"),
              ConfigFiles.marker(ConfigFiles.JS_MARKER, null) + NodeConfigTemplates.vitest()),
          true);
    }
    if (ids.contains(JEST)) {
      configs.userConfig(root, JEST).ifPresent(config -> keepOwn(targets, root, config, true));
    }
    retired(targets, root.resolve("jest.config.mjs"));
    retired(targets, root.resolve(BUNFIG));
    if (ids.contains(STRYKER)) {
      target(
          targets,
          root,
          configs.userConfig(root, STRYKER),
          ConfigTarget.write(
              root.resolve("stryker.config.mjs"),
              ConfigFiles.marker(ConfigFiles.JS_MARKER, preset)
                  + NodeStrykerTemplate.stryker(testGate(ids), preset, project.framework())),
          false);
    }
    return targets;
  }

  /**
   * Adds the user's config as kept, or the {@code written} config of Ward; a config kept in {@code
   * package.json} is already part of the plan, so it adds nothing.
   */
  private static void target(
      final List<ConfigTarget> targets,
      final Path root,
      final Optional<Path> userConfig,
      final ConfigTarget written,
      final boolean coverageTool) {
    if (userConfig.isEmpty()) {
      targets.add(written);
    } else {
      keepOwn(targets, root, userConfig.get(), coverageTool);
    }
  }

  /** Lists the user's config as kept, with a note when it may hold a coverage threshold. */
  private static void keepOwn(
      final List<ConfigTarget> targets,
      final Path root,
      final Path config,
      final boolean coverageTool) {
    if (!config.equals(root.resolve(PACKAGE_JSON))) {
      targets.add(
          new ConfigTarget(
              config,
              Disposition.KEEP,
              null,
              coverageTool
                  ? "kept: your "
                      + config.getFileName()
                      + "; a coverage threshold it sets still applies to the tool, and Ward reads"
                      + " only the minimum of the coverage check in ward.toml"
                  : null));
    }
  }

  /**
   * Removes a coverage config that Ward wrote with a threshold in an earlier version, since it
   * holds nothing else. A file without Ward's marker is the project's own and is not touched.
   */
  private static void retired(final List<ConfigTarget> targets, final Path file)
      throws IOException {
    if (Files.isRegularFile(file) && ConfigFiles.owned(Files.readString(file))) {
      targets.add(
          new ConfigTarget(
              file,
              Disposition.REMOVE,
              null,
              "removed: Ward's earlier " + file.getFileName() + "; the minimum is in ward.toml"));
    }
  }

  private static String testGate(final List<String> ids) {
    return ids.contains(JEST) ? JEST : ids.contains(BUN_TEST) ? BUN_TEST : VITEST;
  }
}
