package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tool configs init writes in a Python project root: Ruff, mypy and coverage. A config the
 * project keeps itself is reused instead, and a config inside {@code pyproject.toml}, {@code
 * setup.cfg} or {@code tox.ini} is never edited, because Ward does not own those files.
 */
final class PythonConfigFiles {
  static final String RUFF = "ruff";
  static final String MYPY = "mypy";
  static final String COVERAGE = "coverage";
  private static final String PYPROJECT = "pyproject.toml";
  private static final Set<String> SHARED_FILES = Set.of(PYPROJECT, "setup.cfg", "tox.ini");

  private static final String RUFF_STANDARD =
      """
      extend-exclude = [".venv", ".ward", "mutants"]

      [lint]
      select = ["E", "F", "I", "B", "UP"]
      """;

  private static final String MYPY_STANDARD =
      """
      [mypy]
      exclude = (^|/)(\\.venv|\\.ward|mutants)/
      warn_unused_ignores = True
      """;

  private static final String COVERAGE_STANDARD =
      """
      [run]
      omit =
          .venv/*
          .ward/*
          mutants/*

      [report]
      fail_under = 80
      show_missing = True
      """;

  private PythonConfigFiles() {}

  /** Returns each config file the gates need, in the order init writes them. */
  static List<NodeConfigTarget> targets(
      final Project project,
      final PythonToolConfigs configs,
      final QualityPreset preset,
      final List<InitGateOption> gates) {
    final Path root = project.root();
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final List<NodeConfigTarget> targets = new ArrayList<>();
    final String marker = NodeConfigFiles.marker(NodeConfigFiles.TOML_MARKER, preset);
    if (ids.contains("ruff-format") || ids.contains("ruff")) {
      target(targets, root, configs, new Own(RUFF, "ruff.toml", marker + RUFF_STANDARD));
    }
    if (ids.contains("mypy")) {
      target(targets, root, configs, new Own(MYPY, "mypy.ini", marker + MYPY_STANDARD));
    }
    if (ids.contains("coverage")) {
      target(targets, root, configs, new Own(COVERAGE, ".coveragerc", marker + COVERAGE_STANDARD));
    }
    return targets;
  }

  /**
   * Adds the project's own config as kept, or Ward's config when the project has none. A config in
   * a shared file adds nothing, because the plan already lists that file.
   */
  private static void target(
      final List<NodeConfigTarget> targets,
      final Path root,
      final PythonToolConfigs configs,
      final Own own) {
    final Optional<Path> userConfig = configs.userConfig(root, own.tool());
    if (userConfig.isEmpty()) {
      targets.add(
          new NodeConfigTarget(
              root.resolve(own.file()), NodeConfigTarget.Disposition.WRITE, own.content(), null));
    } else if (!isShared(root, userConfig.get())) {
      targets.add(
          new NodeConfigTarget(userConfig.get(), NodeConfigTarget.Disposition.KEEP, null, null));
    }
  }

  /** Returns whether {@code config} is one of the shared files Ward never edits. */
  private static boolean isShared(final Path root, final Path config) {
    return SHARED_FILES.stream().anyMatch(name -> config.equals(root.resolve(name)));
  }

  /** A config Ward writes when the project has none: its tool, file name and content. */
  private record Own(String tool, String file, String content) {}
}
