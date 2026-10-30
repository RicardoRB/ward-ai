package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.infrastructure.project.PythonFrameworks;
import java.io.IOException;
import java.nio.file.Files;
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
  static final String PYTEST = "pytest";
  private static final String PYPROJECT = "pyproject.toml";
  private static final Set<String> SHARED_FILES = Set.of(PYPROJECT, "setup.cfg", "tox.ini");

  // The pydantic plugin checks models and their fields, which plain mypy checks only in part.
  private static final String PYDANTIC_MYPY =
      "plugins = pydantic.mypy\n\n[pydantic-mypy]\ninit_typed = True\nwarn_required_dynamic_aliases = True\n";

  private static final String RUFF_STANDARD =
      """
      extend-exclude = [".venv", ".ward", "mutants"]

      [lint]
      select = ["E", "F", "I", "B", "UP"]
      """;

  private static final String RUFF_STRICT =
      """
      extend-exclude = [".venv", ".ward", "mutants"]

      [lint]
      select = ["E", "F", "I", "B", "UP", "W", "C4", "SIM", "RUF", "PTH", "C90"]

      [lint.mccabe]
      max-complexity = 10
      """;

  private static final String MYPY_STANDARD =
      """
      [mypy]
      exclude = (^|/)(\\.venv|\\.ward|mutants)/
      warn_unused_ignores = True
      """;

  private static final String MYPY_STRICT =
      """
      [mypy]
      exclude = (^|/)(\\.venv|\\.ward|mutants)/
      strict = True
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

  private static final String COVERAGE_STRICT =
      """
      [run]
      branch = True
      omit =
          .venv/*
          .ward/*
          mutants/*

      [report]
      fail_under = 85
      show_missing = True
      """;

  private PythonConfigFiles() {}

  /** Returns each config file the gates need, in the order init writes them. */
  static List<NodeConfigTarget> targets(
      final Project project,
      final PythonToolConfigs configs,
      final QualityPreset preset,
      final List<InitGateOption> gates) {
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final List<NodeConfigTarget> targets = new ArrayList<>();
    final Path root = project.root();
    final String marker = NodeConfigFiles.marker(NodeConfigFiles.TOML_MARKER, preset);
    final boolean strict = preset == QualityPreset.STRICT;
    if (ids.contains("ruff-format") || ids.contains("ruff")) {
      target(
          targets,
          root,
          configs,
          new Own(RUFF, "ruff.toml", marker + (strict ? RUFF_STRICT : RUFF_STANDARD)));
    }
    if (ids.contains("mypy")) {
      mypyTarget(targets, project, configs, marker, strict);
    }
    if (ids.contains("pytest")) {
      pytestTarget(project, configs, marker).ifPresent(targets::add);
    }
    if (ids.contains("coverage")) {
      target(
          targets,
          root,
          configs,
          new Own(
              COVERAGE, ".coveragerc", marker + (strict ? COVERAGE_STRICT : COVERAGE_STANDARD)));
    }
    return targets;
  }

  /**
   * Adds mypy's config. A project's own config is kept, and for Django the plan warns when it lacks
   * the plugin, since mypy then reports false errors on the models.
   */
  private static void mypyTarget(
      final List<NodeConfigTarget> targets,
      final Project project,
      final PythonToolConfigs configs,
      final String marker,
      final boolean strict) {
    final Optional<Path> userConfig = configs.userConfig(project.root(), MYPY);
    if (project.framework() == Framework.DJANGO
        && userConfig.isPresent()
        && !hasDjangoPlugin(userConfig.get())) {
      targets.add(
          new NodeConfigTarget(
              userConfig.get(),
              NodeConfigTarget.Disposition.KEEP,
              null,
              "mypy has no Django plugin, so it reports false errors on models. Add `plugins ="
                  + " mypy_django_plugin.main` and a [mypy.plugins.django-stubs] section with"
                  + " django_settings_module set to the settings module."));
      return;
    }
    target(
        targets,
        project.root(),
        configs,
        new Own(MYPY, "mypy.ini", mypyContent(project, marker, strict)));
  }

  /** Returns whether the config file names the Django mypy plugin. */
  private static boolean hasDjangoPlugin(final Path config) {
    try {
      return Files.readString(config).contains("mypy_django_plugin");
    } catch (IOException e) {
      throw new WardException("Could not read " + config + ": " + e.getMessage(), e);
    }
  }

  /** Returns mypy's config, with the plugin of the project's framework: Django's or pydantic's. */
  private static String mypyContent(
      final Project project, final String marker, final boolean strict) {
    final String mypy = marker + (strict ? MYPY_STRICT : MYPY_STANDARD);
    if (project.framework() == Framework.FASTAPI) {
      return mypy + PYDANTIC_MYPY;
    }
    return PythonFrameworks.djangoSettings(project.root(), project.framework())
        .map(settings -> mypy + djangoMypy(settings))
        .orElse(mypy);
  }

  /**
   * Returns a pytest.ini naming the Django settings, when the project is Django and has no pytest
   * config.
   */
  private static Optional<NodeConfigTarget> pytestTarget(
      final Project project, final PythonToolConfigs configs, final String marker) {
    return PythonFrameworks.djangoSettings(project.root(), project.framework())
        .filter(settings -> configs.userConfig(project.root(), PYTEST).isEmpty())
        .map(
            settings ->
                new NodeConfigTarget(
                    project.root().resolve("pytest.ini"),
                    NodeConfigTarget.Disposition.WRITE,
                    marker + "[pytest]\nDJANGO_SETTINGS_MODULE = " + settings + "\n",
                    null));
  }

  /** The mypy plugin section that makes django-stubs read the project's settings module. */
  private static String djangoMypy(final String settings) {
    return "plugins = mypy_django_plugin.main\n\n[mypy.plugins.django-stubs]\ndjango_settings_module = "
        + "\""
        + settings
        + "\"\n";
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
