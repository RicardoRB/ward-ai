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
    final TomlArray array = parsed.getArrayOrEmpty("package");
    final List<TomlTable> tables = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      tables.add(array.getTable(index));
    }
    final Map<String, List<PackageId>> byName = new LinkedHashMap<>();
    tables.forEach(
        table -> byName.computeIfAbsent(nameOf(table), key -> new ArrayList<>()).add(id(table)));
    final PythonLock.Collector collector = new PythonLock.Collector();
    tables.forEach(table -> collector.add(id(table), released(table), required(table, byName)));
    final Map<String, PythonLock.Resolved> directs = new LinkedHashMap<>();
    byName.forEach((name, ids) -> directs.put(name, new PythonLock.Resolved(ids, false)));
    return new PythonLock(collector.packages(), Map.of("", directs));
  }

  private static String nameOf(final TomlTable table) {
    final String name = table.getString("name");
    if (name == null || table.getString("version") == null) {
      throw new LockfileFormatException("a package has no name or version");
    }
    return PyprojectDependencies.normalize(name);
  }

  private static PackageId id(final TomlTable table) {
    return PythonLock.pypi(nameOf(table), table.getString("version"));
  }

  private static Set<PackageId> required(
      final TomlTable table, final Map<String, List<PackageId>> byName) {
    final Set<PackageId> required = new HashSet<>();
    final TomlTable dependencies = table.getTable("dependencies");
    if (dependencies != null) {
      dependencies
          .keySet()
          .forEach(
              dependency ->
                  required.addAll(
                      byName.getOrDefault(PyprojectDependencies.normalize(dependency), List.of())));
    }
    return required;
  }

  private static boolean released(final TomlTable table) {
    final String type = table.getString("source.type");
    return type == null || !NON_RELEASE_SOURCES.contains(type);
  }
}
