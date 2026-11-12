package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.python.PyprojectDependencies;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * What a module's {@code pyproject.toml} declares, with the scope of each declaring section: main
 * and extra dependencies are runtime, dependency groups are development, and a group named {@code
 * test} or {@code tests} is test.
 *
 * @param path the module path relative to the project root
 * @param manifest the project-relative path of its pyproject.toml
 * @param lockKey the key of the module in the lockfile: the normalized project name for uv
 * @param declared the normalized names it declares, with their widest scope
 */
record PythonManifest(
    String path, String manifest, String lockKey, Map<String, DependencyScope> declared) {
  private static final Set<String> TEST_GROUPS = Set.of("test", "tests");

  /** Copies the declared names. */
  PythonManifest {
    declared = Map.copyOf(declared);
  }

  /** Returns whether the manifest declares any package. */
  boolean declaresPackages() {
    return !declared.isEmpty();
  }

  /**
   * Reads the pyproject.toml of {@code dir}, or an empty manifest when there is none.
   *
   * @throws DependencyInventoryException if it is not valid TOML
   */
  static PythonManifest read(final Path root, final Path dir, final Project project) {
    final Path file = dir.resolve("pyproject.toml");
    final String path = LockFiles.relative(root, dir);
    final String manifest = LockFiles.join(path, "pyproject.toml");
    if (!Files.isRegularFile(file)) {
      return new PythonManifest(path, manifest, "", Map.of());
    }
    try {
      final TomlParseResult parsed = PyprojectDependencies.parse(Files.readString(file));
      final Map<String, DependencyScope> declared = new LinkedHashMap<>();
      collect(parsed, declared);
      final String name = parsed.getString("project.name");
      return new PythonManifest(
          path, manifest, name == null ? "" : PyprojectDependencies.normalize(name), declared);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), manifest, "it cannot be read", "check its permissions");
    } catch (dev.ward.domain.config.WardException e) {
      throw new DependencyInventoryException(
          project.root().toString(), manifest, e.getMessage(), "fix the TOML syntax");
    }
  }

  private static void collect(
      final TomlParseResult parsed, final Map<String, DependencyScope> declared) {
    requirements(declared, parsed.getArray("project.dependencies"), DependencyScope.RUNTIME);
    groups(declared, parsed.getTable("project.optional-dependencies"), false);
    groups(declared, parsed.getTable("dependency-groups"), true);
    requirements(
        declared, parsed.getArray("tool.uv.dev-dependencies"), DependencyScope.DEVELOPMENT);
    poetry(declared, parsed.getTable("tool.poetry.dependencies"), DependencyScope.RUNTIME);
    poetry(declared, parsed.getTable("tool.poetry.dev-dependencies"), DependencyScope.DEVELOPMENT);
    final TomlTable poetryGroups = parsed.getTable("tool.poetry.group");
    if (poetryGroups != null) {
      poetryGroups
          .keySet()
          .forEach(
              group ->
                  poetry(
                      declared,
                      parsed.getTable("tool.poetry.group." + group + ".dependencies"),
                      groupScope(group)));
    }
  }

  private static DependencyScope groupScope(final String group) {
    return TEST_GROUPS.contains(group) ? DependencyScope.TEST : DependencyScope.DEVELOPMENT;
  }

  private static void groups(
      final Map<String, DependencyScope> declared,
      final TomlTable table,
      final boolean dependencyGroups) {
    if (table != null) {
      table
          .keySet()
          .forEach(
              key ->
                  requirements(
                      declared,
                      table.getArray(key),
                      dependencyGroups ? groupScope(key) : DependencyScope.RUNTIME));
    }
  }

  private static void requirements(
      final Map<String, DependencyScope> declared,
      final TomlArray items,
      final DependencyScope scope) {
    if (items == null) {
      return;
    }
    for (final Object item : items.toList()) {
      if (item instanceof String text) {
        PyprojectDependencies.nameOf(text)
            .ifPresent(name -> declared.merge(name, scope, DependencyScope::widest));
      }
    }
  }

  private static void poetry(
      final Map<String, DependencyScope> declared,
      final TomlTable table,
      final DependencyScope scope) {
    if (table != null) {
      table.keySet().stream()
          .filter(name -> !"python".equals(name))
          .forEach(
              name ->
                  declared.merge(
                      PyprojectDependencies.normalize(name), scope, DependencyScope::widest));
    }
  }
}
