package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.project.PyprojectDependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Adds the pinned tools the selected Python gates need to the project's {@code ward} dependency
 * group, or to {@code requirements-ward.txt} for a project without {@code pyproject.toml}, then
 * installs them with the project's manager. A failed install restores the files it changed.
 */
public final class PythonBuildToolConfigurator implements BuildToolConfiguration {
  private static final Logger LOGGER =
      Logger.getLogger(PythonBuildToolConfigurator.class.getName());
  private static final String PYPROJECT = "pyproject.toml";
  private static final String SRC_NOTE =
      "src/ layout without [build-system]: pip will not install the package, so tests cannot import"
          + " it. Add a [build-system] table to pyproject.toml.";
  static final String REQUIREMENTS = "requirements-ward.txt";
  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs the package manager's install. */
  @Inject
  public PythonBuildToolConfigurator(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    final List<String> problems = PythonInstall.problems(project, executor);
    if (!problems.isEmpty()) {
      throw new WardException(problems.getFirst());
    }
    final Edit edit = edit(project, gates);
    if (edit.missing().isEmpty()) {
      return new BuildFileChange(Toolchain.PYTHON, null, null, List.of());
    }
    final Path lock = PythonInstall.lockFile(project);
    final BuildFileChange change =
        new BuildFileChange(
            Toolchain.PYTHON,
            edit.file(),
            edit.original(),
            edit.missing().stream().map(PythonPackageCatalog.Package::name).toList(),
            List.of(),
            lock,
            lock != null && Files.isRegularFile(lock) ? read(lock) : null,
            List.of());
    write(edit.file(), edit.edited());
    LOGGER.log(Level.INFO, () -> "event=python-dependencies-added count=" + edit.missing().size());
    install(project, change, commands(project, edit));
    return change;
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Edit edit = edit(project, gates);
    final List<String> problems = PythonInstall.problems(project, executor);
    if (!problems.isEmpty()) {
      return new PlannedFileChange(edit.file(), PlannedFileChange.Action.CONFLICT, problems);
    }
    if (edit.missing().isEmpty()) {
      return new PlannedFileChange(edit.file(), PlannedFileChange.Action.UNCHANGED, List.of());
    }
    final List<String> details = new ArrayList<>();
    edit.missing()
        .forEach(
            tool ->
                details.add(
                    "add " + tool.requirement() + " to the " + PyprojectEdit.GROUP + " group"));
    commands(project, edit).forEach(command -> details.add("run " + String.join(" ", command)));
    if (isSrcWithoutBuildSystem(project, edit)) {
      details.add(SRC_NOTE);
    }
    return new PlannedFileChange(edit.file(), PlannedFileChange.Action.MODIFY, details);
  }

  /** A src layout is importable only once pip installs the project, which needs a build system. */
  private static boolean isSrcWithoutBuildSystem(final Project project, final Edit edit) {
    return project.buildTool() == BuildTool.PIP
        && edit.pyproject()
        && !edit.buildSystem()
        && Files.isDirectory(project.root().resolve("src"));
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null) {
      return;
    }
    LOGGER.log(Level.INFO, "event=python-rollback");
    restore(change.file(), change.originalContent());
    if (change.lockFile() != null) {
      restore(change.lockFile(), change.lockOriginal());
    }
  }

  /** The build file as init would edit it, with the tools that were missing from it. */
  private record Edit(
      Path file,
      boolean pyproject,
      boolean buildSystem,
      String original,
      String edited,
      List<PythonPackageCatalog.Package> missing) {}

  private static Edit edit(final Project project, final List<InitGateOption> gates) {
    final Path root = project.root();
    final boolean pyproject = Files.isRegularFile(root.resolve(PYPROJECT));
    final Path file = root.resolve(pyproject ? PYPROJECT : REQUIREMENTS);
    final String original = Files.isRegularFile(file) ? read(file) : null;
    final Set<String> declared =
        pyproject
            ? PyprojectDependencies.declared(original)
            : PythonRequirementsFile.declaredNames(root);
    final List<PythonPackageCatalog.Package> missing =
        PythonPackageCatalog.missing(gates, declared, project.framework()).stream()
            // pytest-flask needs an app factory; a Flask app without one imports its app directly.
            .filter(
                tool ->
                    !PythonPackageCatalog.PYTEST_FLASK.equals(tool.name())
                        || FlaskPackages.hasAppFactory(project))
            .toList();
    final boolean buildSystem =
        pyproject && PyprojectDependencies.parse(original).contains("build-system");
    if (missing.isEmpty()) {
      return new Edit(file, pyproject, buildSystem, original, original, missing);
    }
    final List<String> requirements =
        missing.stream().map(PythonPackageCatalog.Package::requirement).toList();
    final String edited =
        pyproject
            ? PyprojectEdit.withGroup(original, requirements)
            : PythonRequirementsFile.withRequirements(original, requirements);
    return new Edit(file, pyproject, buildSystem, original, edited, missing);
  }

  private static List<List<String>> commands(final Project project, final Edit edit) {
    return PythonInstall.commands(
        project, edit.pyproject(), edit.buildSystem(), PyprojectEdit.GROUP);
  }

  private void install(
      final Project project, final BuildFileChange change, final List<List<String>> commands) {
    for (final List<String> command : commands) {
      LOGGER.log(
          Level.INFO, () -> "event=python-install-start manager=" + project.buildExecutable());
      final CommandResult result = executor.execute(command, project.root());
      LOGGER.log(Level.INFO, () -> "event=python-install-complete exit=" + result.exitCode());
      if (result.exitCode() != 0) {
        rollback(change);
        throw PythonInstall.failed(command, result.exitCode());
      }
    }
  }

  private static void restore(final Path file, final String original) {
    try {
      if (original == null) {
        Files.deleteIfExists(file);
      } else {
        Files.writeString(file, original);
      }
    } catch (IOException e) {
      throw new WardException("Could not restore " + file + ": " + e.getMessage(), e);
    }
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }

  private static void write(final Path file, final String content) {
    try {
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new WardException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }
}
