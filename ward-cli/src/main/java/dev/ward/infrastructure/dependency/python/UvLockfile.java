package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.infrastructure.project.python.PyprojectDependencies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads {@code uv.lock} (format version 1). The root project and every workspace member, which uv
 * writes with an {@code editable} or {@code virtual} source, are modules and not packages: what
 * they resolve flows through to whoever depends on them.
 */
final class UvLockfile {
  private static final String NAME = "name";
  private static final String DEPENDENCIES = "dependencies";
  private static final String OPTIONAL = "optional-dependencies";
  private static final String DEV = "dev-dependencies";
  private static final String VERSION = "version";
  private static final int SUPPORTED = 1;

  private final Map<String, List<Entry>> byName = new LinkedHashMap<>();

  /** One {@code [[package]]} table. */
  private record Entry(
      String name, String version, boolean member, boolean registry, TomlTable table) {
    static Entry of(
        final String name,
        final String version,
        final boolean member,
        final boolean registry,
        final TomlTable table) {
      return new Entry(
          name, version == null ? "" : version, member || version == null, registry, table);
    }

    PackageId id() {
      return PythonLock.pypi(name, version);
    }
  }

  private UvLockfile() {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not valid TOML or has another format version
   */
  static PythonLock read(final String text) {
    final TomlParseResult parsed = TomlSupport.parse(text);
    final Long version = parsed.getLong(VERSION);
    if (version == null || version != SUPPORTED) {
      throw new LockfileFormatException("unsupported lock version " + version);
    }
    final UvLockfile lock = new UvLockfile();
    final TomlArray packages = parsed.getArrayOrEmpty("package");
    for (int index = 0; index < packages.size(); index++) {
      lock.add(packages.getTable(index));
    }
    return lock.toLock();
  }

  private void add(final TomlTable table) {
    final String name = table.getString(NAME);
    if (name == null) {
      throw new LockfileFormatException("a package has no name");
    }
    final TomlTable source = table.getTable("source");
    final boolean member =
        source != null && (source.contains("editable") || source.contains("virtual"));
    final String version = table.getString(VERSION);
    final Entry entry =
        Entry.of(
            PyprojectDependencies.normalize(name),
            version,
            member,
            source != null && source.contains("registry"),
            table);
    byName.computeIfAbsent(entry.name(), key -> new ArrayList<>()).add(entry);
  }

  private PythonLock toLock() {
    final PythonLock.Collector collector = new PythonLock.Collector();
    final Map<String, Map<String, PythonLock.Resolved>> directs = new HashMap<>();
    byName.values().stream()
        .flatMap(List::stream)
        .forEach(
            entry -> {
              if (entry.member()) {
                directs.put(entry.name(), directsOf(entry));
              } else {
                collector.add(entry.id(), entry.registry(), required(entry));
              }
            });
    return new PythonLock(collector.packages(), directs);
  }

  private Set<PackageId> required(final Entry entry) {
    final Set<PackageId> required = new HashSet<>();
    refs(entry.table(), true).forEach(ref -> required.addAll(real(ref, new HashSet<>())));
    return required;
  }

  private Map<String, PythonLock.Resolved> directsOf(final Entry module) {
    final Map<String, PythonLock.Resolved> directs = new LinkedHashMap<>();
    refs(module.table(), false).forEach(ref -> direct(directs, ref));
    return directs;
  }

  private void direct(final Map<String, PythonLock.Resolved> directs, final TomlTable ref) {
    final Entry target = target(ref);
    if (target != null) {
      directs.put(
          PyprojectDependencies.normalize(ref.getString(NAME)),
          new PythonLock.Resolved(new ArrayList<>(real(ref, new HashSet<>())), target.member()));
    }
  }

  /** Returns the packages {@code ref} stands for, passing through members and local projects. */
  private List<PackageId> real(final TomlTable ref, final Set<String> visited) {
    final Entry target = target(ref);
    if (target == null) {
      return List.of();
    }
    if (!target.member()) {
      return List.of(target.id());
    }
    final List<PackageId> through = new ArrayList<>();
    if (visited.add(target.name())) {
      refs(target.table(), true).forEach(next -> through.addAll(real(next, visited)));
    }
    return through;
  }

  /**
   * Returns the dependency references of a package: the default ones plus the extras they request
   * (the requested extra's packages are followed for every user of the package, a documented
   * shortcut); with {@code defaultOnly} false, every extra and dependency group instead.
   */
  private List<TomlTable> refs(final TomlTable table, final boolean defaultOnly) {
    final List<TomlTable> refs = new ArrayList<>();
    for (final TomlTable ref : TomlSupport.tables(table.getArray(DEPENDENCIES))) {
      refs.add(ref);
      if (defaultOnly) {
        refs.addAll(requestedExtras(ref));
      }
    }
    if (!defaultOnly) {
      for (final String section : List.of(OPTIONAL, DEV)) {
        final TomlTable group = table.getTable(section);
        if (group != null) {
          group.keySet().forEach(key -> refs.addAll(TomlSupport.tables(group.getArray(key))));
        }
      }
    }
    return refs;
  }

  private List<TomlTable> requestedExtras(final TomlTable ref) {
    final List<TomlTable> extras = new ArrayList<>();
    final Entry target = target(ref);
    final TomlArray requested = ref.getArray("extra");
    final TomlTable optional = target == null ? null : target.table().getTable(OPTIONAL);
    if (requested != null && optional != null) {
      for (int index = 0; index < requested.size(); index++) {
        extras.addAll(TomlSupport.tables(optional.getArray(requested.getString(index))));
      }
    }
    return extras;
  }

  private Entry target(final TomlTable ref) {
    final String name = ref.getString(NAME);
    final List<Entry> candidates =
        byName.get(PyprojectDependencies.normalize(String.valueOf(name)));
    if (candidates == null) {
      return null;
    }
    final String version = ref.getString(VERSION);
    return candidates.stream()
        .filter(entry -> version == null || entry.version().equals(version))
        .findFirst()
        .orElse(null);
  }
}
