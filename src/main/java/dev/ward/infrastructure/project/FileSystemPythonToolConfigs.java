package dev.ward.infrastructure.project;

import dev.ward.domain.project.PythonToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Finds a Python tool's own config in the project root: a standalone file, a table in {@code
 * pyproject.toml}, or a section of {@code setup.cfg} or {@code tox.ini}. A file that Ward wrote
 * (its marker is the first line) does not count.
 */
public final class FileSystemPythonToolConfigs implements PythonToolConfigs {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String WARD_MARKER = "# managed-by: ward";
  private static final Map<String, List<String>> FILES =
      Map.of(
          "ruff", List.of("ruff.toml", ".ruff.toml"),
          "mypy", List.of("mypy.ini", ".mypy.ini"),
          "coverage", List.of(".coveragerc"),
          "import-linter", List.of(".importlinter"));
  private static final Map<String, String> TABLES =
      Map.of(
          "ruff", "tool.ruff",
          "mypy", "tool.mypy",
          "coverage", "tool.coverage",
          "import-linter", "tool.importlinter");
  private static final Map<String, String> INI_SECTIONS =
      Map.of("mypy", "mypy", "coverage", "coverage:", "import-linter", "importlinter");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemPythonToolConfigs() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<Path> userConfig(final Path root, final String tool) {
    final Optional<Path> file = standalone(root, tool);
    if (file.isPresent()) {
      return file;
    }
    final String pyproject = read(root.resolve(PYPROJECT));
    if (pyproject != null && hasTable(pyproject, TABLES.get(tool))) {
      return Optional.of(root.resolve(PYPROJECT));
    }
    return iniSection(root, tool);
  }

  private static Optional<Path> standalone(final Path root, final String tool) {
    for (final String name : FILES.getOrDefault(tool, List.of())) {
      final Path file = root.resolve(name);
      final String content = read(file);
      if (content != null && !content.startsWith(WARD_MARKER)) {
        return Optional.of(file);
      }
    }
    return Optional.empty();
  }

  private static boolean hasTable(final String toml, final String table) {
    if (table == null) {
      return false;
    }
    // A pyproject.toml that does not parse keeps no tool config that Ward can read.
    final TomlParseResult parsed = Toml.parse(toml);
    return !parsed.hasErrors() && parsed.contains(table);
  }

  private static Optional<Path> iniSection(final Path root, final String tool) {
    final String section = INI_SECTIONS.get(tool);
    if (section == null) {
      return Optional.empty();
    }
    for (final String name : List.of("setup.cfg", "tox.ini")) {
      final Path file = root.resolve(name);
      final String content = read(file);
      if (content != null && hasSection(content, section)) {
        return Optional.of(file);
      }
    }
    return Optional.empty();
  }

  /** Returns whether {@code ini} holds a {@code [section]} header, or one starting with it. */
  private static boolean hasSection(final String ini, final String section) {
    return ini.lines()
        .map(String::strip)
        .anyMatch(
            line ->
                line.startsWith("[")
                    && line.endsWith("]")
                    && (line.equals("[" + section + "]")
                        || section.endsWith(":") && line.startsWith("[" + section)));
  }

  private static String read(final Path file) {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return null;
    }
  }
}
