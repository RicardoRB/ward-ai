package dev.ward.infrastructure.init.python;

import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonSupport;
import dev.ward.infrastructure.project.PyprojectDependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a Python project can take the tools and which commands install them: the
 * manager's sync or install for the {@code ward} group, or pip for a requirements file.
 */
final class PythonInstall {
  private static final String VENV_PYTHON = ".venv/bin/python";
  private static final String VENV = ".venv";
  private static final String POETRY_MINIMUM = "2.2";
  private static final String PIP_MINIMUM = "25.1";
  private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");
  private static final String PYPROJECT = "pyproject.toml";
  private static final Pattern VERSION_MAJOR = Pattern.compile("(\\d+)\\.");

  private PythonInstall() {}

  /**
   * Returns the blocking reasons init cannot install the Python tools, empty when it can: an
   * unsupported Python, a pip without its virtual environment, or a manager too old to read the
   * {@code ward} group.
   */
  static List<String> problems(final Project project, final CommandExecutor executor) {
    final List<String> problems = new ArrayList<>();
    PythonSupport.incompatibility(project).ifPresent(problems::add);
    if (project.buildTool() == BuildTool.PIP && !Files.isDirectory(project.root().resolve(VENV))) {
      problems.add(
          "pip installs into .venv, which this project does not have. Create it with `python -m venv"
              + " .venv`, then run init again.");
    }
    managerTooOld(project, executor).ifPresent(problems::add);
    problems.addAll(declaredOutsideRange(project));
    return problems;
  }

  /**
   * Returns a blocking reason for each managed tool the project declares at a major version other
   * than the one Ward verified; init then stops without changing the declaration.
   */
  private static List<String> declaredOutsideRange(final Project project) {
    final Path file = project.root().resolve(PYPROJECT);
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    final Map<String, String> declared;
    try {
      declared = PyprojectDependencies.requirements(Files.readString(file));
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
    return PythonPackageCatalog.packages().stream()
        .filter(tool -> declared.containsKey(PyprojectDependencies.normalize(tool.name())))
        .filter(
            tool ->
                !declaredMajor(declared.get(PyprojectDependencies.normalize(tool.name())))
                    .equals(String.valueOf(tool.major())))
        .map(
            tool ->
                "INCOMPATIBLE "
                    + declared.get(PyprojectDependencies.normalize(tool.name()))
                    + ": Ward verified "
                    + tool.name()
                    + " "
                    + tool.version()
                    + "; declare a "
                    + tool.major()
                    + ".x version, or remove the requirement so init can pin one.")
        .toList();
  }

  /** Returns the first major version in a requirement, or empty when it names none. */
  private static String declaredMajor(final String requirement) {
    final Matcher version = VERSION_MAJOR.matcher(requirement);
    return version.find() ? version.group(1) : "";
  }

  /** Returns the reason when the manager\'s version is below the minimum it can install. */
  private static Optional<String> managerTooOld(
      final Project project, final CommandExecutor executor) {
    final Path root = project.root();
    if (project.buildTool() == BuildTool.POETRY) {
      return tooOld(executor, root, List.of("poetry", "--version"), "Poetry", POETRY_MINIMUM);
    }
    if (project.buildTool() == BuildTool.PIP && Files.isRegularFile(root.resolve(PYPROJECT))) {
      return tooOld(
          executor, root, List.of(VENV_PYTHON, "-m", "pip", "--version"), "pip", PIP_MINIMUM);
    }
    return Optional.empty();
  }

  /** Returns the reason when {@code command} reports a version below {@code minimum}. */
  private static Optional<String> tooOld(
      final CommandExecutor executor,
      final Path root,
      final List<String> command,
      final String manager,
      final String minimum) {
    final CommandResult result = executor.execute(command, root);
    if (!result.succeeded()) {
      return Optional.empty();
    }
    final Matcher version = VERSION.matcher(result.stdout());
    if (!version.find() || atLeast(version, minimum)) {
      return Optional.empty();
    }
    return Optional.of(
        manager
            + " "
            + version.group(0)
            + " is too old; Ward needs "
            + manager
            + " "
            + minimum
            + " or newer to install the "
            + PyprojectEdit.GROUP
            + " group. Upgrade "
            + manager
            + " and run init again.");
  }

  /** Returns whether the matched {@code major.minor} is at least {@code minimum}. */
  private static boolean atLeast(final Matcher version, final String minimum) {
    final Matcher floor = VERSION.matcher(minimum);
    floor.matches();
    final int major = Integer.parseInt(version.group(1));
    final int minor = Integer.parseInt(version.group(2));
    final int floorMajor = Integer.parseInt(floor.group(1));
    final int floorMinor = Integer.parseInt(floor.group(2));
    return major > floorMajor || major == floorMajor && minor >= floorMinor;
  }

  /**
   * Returns the install commands, in order, for the manager and the file that was edited. {@code
   * buildSystem} says the project is installed as a package, which pip does with an editable
   * install.
   */
  static List<List<String>> commands(
      final Project project,
      final boolean pyproject,
      final boolean buildSystem,
      final String group) {
    return switch (project.buildTool()) {
      case POETRY ->
          List.of(List.of("poetry", "lock"), List.of("poetry", "install", "--with", group));
      case PIP -> pipCommands(pyproject, buildSystem, group);
      default -> List.of(List.of("uv", "sync", "--group", group));
    };
  }

  /**
   * Returns pip's commands: the group from {@code pyproject.toml}, then the project itself when it
   * has a build system, because a src layout imports only once installed. Without a pyproject the
   * requirements file is the install.
   */
  private static List<List<String>> pipCommands(
      final boolean pyproject, final boolean buildSystem, final String group) {
    if (!pyproject) {
      return List.of(
          List.of(
              VENV_PYTHON, "-m", "pip", "install", "-r", PythonBuildToolConfigurator.REQUIREMENTS));
    }
    final List<List<String>> commands = new ArrayList<>();
    commands.add(List.of(VENV_PYTHON, "-m", "pip", "install", "--group", group));
    if (buildSystem) {
      commands.add(List.of(VENV_PYTHON, "-m", "pip", "install", "-e", "."));
    }
    return List.copyOf(commands);
  }

  /** Returns the lock file the install writes, or {@code null} for pip, which keeps none. */
  static Path lockFile(final Project project) {
    final Path root = project.root();
    return switch (project.buildTool()) {
      case UV -> root.resolve("uv.lock");
      case POETRY -> root.resolve("poetry.lock");
      default -> null;
    };
  }

  /** Returns the error for an install command that exited with {@code exitCode}. */
  static WardException failed(final List<String> command, final int exitCode) {
    return new WardException(
        "`"
            + String.join(" ", command)
            + "` failed with exit code "
            + exitCode
            + "; the files init changed were restored.");
  }
}
