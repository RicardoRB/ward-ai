package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.infrastructure.project.python.PyprojectDependencies;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads {@code poetry.lock} (lock-version 1.x and 2.x). Poetry locks one project, so the module key
 * is empty. Every package of the lockfile is installed, so every name a package lists is followed.
 */
final class PoetryLockfile {
  private static final Set<String> NON_RELEASE_SOURCES = Set.of("git", "directory", "url", "file");

  private PoetryLockfile() {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not valid TOML or has another lock-version
   */
  static PythonLock read(final String text) {
    final TomlParseResult parsed = TomlSupport.parse(text);
    final String version = parsed.getString("metadata.lock-version");
    if (version == null || !(version.startsWith("1.") || version.startsWith("2."))) {
      throw new LockfileFormatException("unsupported lock-version " + version);
    }
    final Map<String, List<PackageId>> byName = new LinkedHashMap<>();
    final TomlArray packages = parsed.getArrayOrEmpty("package");
    for (int index = 0; index < packages.size(); index++) {
      final TomlTable table = packages.getTable(index);
      final String name = table.getString("name");
      final String packageVersion = table.getString("version");
      if (name == null || packageVersion == null) {
        throw new LockfileFormatException("a package has no name or version");
      }
      final String normalized = PyprojectDependencies.normalize(name);
      byName
          .computeIfAbsent(normalized, key -> new ArrayList<>())
          .add(PythonLock.pypi(normalized, packageVersion));
    }
    final PythonLock.Collector collector = new PythonLock.Collector();
    for (int index = 0; index < packages.size(); index++) {
      final TomlTable table = packages.getTable(index);
      final Set<PackageId> required = new HashSet<>();
      final TomlTable dependencies = table.getTable("dependencies");
      if (dependencies != null) {
        dependencies
            .keySet()
            .forEach(
                dependency ->
                    required.addAll(
                        byName.getOrDefault(
                            PyprojectDependencies.normalize(dependency), List.of())));
      }
      collector.add(
          PythonLock.pypi(
              PyprojectDependencies.normalize(table.getString("name")), table.getString("version")),
          released(table),
          required);
    }
    final Map<String, PythonLock.Resolved> directs = new LinkedHashMap<>();
    byName.forEach((name, ids) -> directs.put(name, new PythonLock.Resolved(ids, false)));
    return new PythonLock(collector.packages(), Map.of("", directs));
  }

  private static boolean released(final TomlTable table) {
    final String type = table.getString("source.type");
    return type == null || !NON_RELEASE_SOURCES.contains(type);
  }
}
