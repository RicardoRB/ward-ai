package dev.ward.infrastructure.project;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads a Python project's package manager and target version from its files. */
final class PythonProjectInspection {
  static final String PYPROJECT = "pyproject.toml";
  static final String SETUP_PY = "setup.py";
  static final String SETUP_CFG = "setup.cfg";
  private static final String PYTHON_VERSION = ".python-version";
  private static final String REQUIREMENTS = "requirements";
  private static final String TXT = ".txt";
  private static final String UV_LOCK = "uv.lock";
  private static final String POETRY_LOCK = "poetry.lock";
  private static final String PDM_LOCK = "pdm.lock";
  private static final String TOOL_UV = "tool.uv";
  private static final String TOOL_POETRY = "tool.poetry";
  private static final String TOOL_HATCH = "tool.hatch";
  private static final String REQUIRES_PYTHON = "project.requires-python";
  private static final String VENV = ".venv";
  private static final String DEFAULT_PYTHON_VERSION = "3.14";
  private static final Pattern MAJOR_MINOR = Pattern.compile("\\d+\\.\\d+");
  private static final Logger LOGGER = Logger.getLogger(PythonProjectInspection.class.getName());

  private PythonProjectInspection() {}

  /** Returns whether {@code dir} holds a Python marker: a build file or a requirements file. */
  static boolean hasMarker(final Path dir) {
    if (Stream.of(PYPROJECT, SETUP_PY, SETUP_CFG)
        .anyMatch(file -> Files.isRegularFile(dir.resolve(file)))) {
      return true;
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(Path::getFileName)
          .filter(Objects::nonNull)
          .map(Path::toString)
          .anyMatch(PythonProjectInspection::isRequirementsFile);
    } catch (IOException e) {
      return false;
    }
  }

  /** Returns whether a file name is a requirements file, such as {@code requirements-dev.txt}. */
  static boolean isRequirementsFile(final String name) {
    return name.startsWith(REQUIREMENTS) && name.endsWith(TXT);
  }

  /**
   * Returns the Python project at {@code dir}, which holds a Python build file. A {@code .venv} in
   * a pip project is reported as the wrapper, the executable that pip projects run through.
   */
  static Project project(final Path dir) {
    final TomlTable pyproject = pyproject(dir);
    final BuildTool manager = manager(dir, pyproject);
    return new Project(
        dir,
        Language.PYTHON,
        manager,
        PythonFrameworks.of(dir),
        manager == BuildTool.PIP && Files.isDirectory(dir.resolve(VENV)),
        false,
        dir,
        Set.of(Language.PYTHON),
        version(dir, pyproject),
        TaskRunner.NONE);
  }

  /** The first rule that matches decides the manager: uv, Poetry, PDM, Hatch, else pip. */
  private static BuildTool manager(final Path dir, final TomlTable pyproject) {
    if (Files.isRegularFile(dir.resolve(UV_LOCK))) {
      return decided(UV_LOCK, BuildTool.UV);
    }
    if (pyproject.isTable(TOOL_UV)) {
      return decided(PYPROJECT + " [" + TOOL_UV + "]", BuildTool.UV);
    }
    if (Files.isRegularFile(dir.resolve(POETRY_LOCK))) {
      return decided(POETRY_LOCK, BuildTool.POETRY);
    }
    if (pyproject.isTable(TOOL_POETRY)) {
      return decided(PYPROJECT + " [" + TOOL_POETRY + "]", BuildTool.POETRY);
    }
    if (Files.isRegularFile(dir.resolve(PDM_LOCK))) {
      return decided(PDM_LOCK, BuildTool.PDM);
    }
    if (pyproject.isTable(TOOL_HATCH)) {
      return decided(PYPROJECT + " [" + TOOL_HATCH + "]", BuildTool.HATCH);
    }
    return decided("no manager file or table", BuildTool.PIP);
  }

  private static BuildTool decided(final String source, final BuildTool tool) {
    LOGGER.fine(() -> "Python manager " + tool.displayName() + " from " + source);
    return tool;
  }

  /** The target version: .python-version, then requires-python, then the default. */
  private static String version(final Path dir, final TomlTable pyproject) {
    return versionFile(dir).or(() -> requiresPython(pyproject)).orElse(DEFAULT_PYTHON_VERSION);
  }

  private static Optional<String> versionFile(final Path dir) {
    final Path file = dir.resolve(PYTHON_VERSION);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      final String firstLine = Files.readString(file).lines().findFirst().orElse("");
      return majorMinor(firstLine).map(version -> sourced(PYTHON_VERSION, version));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  // shortcut: the first major.minor in requires-python is the lower bound, as Node does for
  // engines.node; an exclusive bound such as >3.10 reads as 3.10, upgrade with a real range parser.
  private static Optional<String> requiresPython(final TomlTable pyproject) {
    final String range = pyproject.getString(REQUIRES_PYTHON);
    return range == null
        ? Optional.empty()
        : majorMinor(range).map(version -> sourced(PYPROJECT + " requires-python", version));
  }

  private static Optional<String> majorMinor(final String text) {
    final Matcher matcher = MAJOR_MINOR.matcher(text);
    return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
  }

  private static String sourced(final String source, final String version) {
    LOGGER.fine(() -> "Python version " + version + " from " + source);
    return version;
  }

  /** Parses pyproject.toml leniently: a missing, unreadable or malformed file is an empty table. */
  static TomlTable pyproject(final Path dir) {
    final Path file = dir.resolve(PYPROJECT);
    if (!Files.isRegularFile(file)) {
      return Toml.parse("");
    }
    try {
      final TomlParseResult result = Toml.parse(Files.readString(file));
      return result.hasErrors() ? Toml.parse("") : result;
    } catch (IOException e) {
      return Toml.parse("");
    }
  }
}
