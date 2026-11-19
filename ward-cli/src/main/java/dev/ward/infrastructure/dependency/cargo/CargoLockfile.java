package dev.ward.infrastructure.dependency.cargo;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * What a {@code Cargo.lock} says: the packages that come from a registry or git, with what each
 * requires. A package without a {@code source} is a path or workspace crate and is left out.
 *
 * @param packages the locked packages with their requirements, by identity
 * @param byName the identities of each normalized crate name
 * @param local the normalized names of the path and workspace crates
 */
record CargoLockfile(
    Map<PackageId,
    Locked> packages,
    Map<String,
    List<PackageId>> byName,
    Set<String> local
) {
  /** A locked package: whether a registry published it, and what it requires. */
  record Locked(boolean released, Set<PackageId> requires) {}

  /** Copies the maps. */
  CargoLockfile {
    packages = Map.copyOf(packages);
    byName = Map.copyOf(byName);
    local = Set.copyOf(local);
  }

  /** Returns a crates.io identity. */
  static PackageId crate(final String name, final String version) {
    return new PackageId(Ecosystem.CRATES_IO, name, version);
  }

  /** Returns {@code name} as crates.io compares names: case and {@code -}/{@code _} blind. */
  static String normalize(final String name) {
    return name.toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not valid TOML or a package has no name or version
   */
  static CargoLockfile read(final String text) {
    final TomlParseResult parsed = Toml.parse(text);
    if (parsed.hasErrors()) {
      throw new LockfileFormatException("it is not valid TOML");
    }
    final TomlArray array = parsed.getArrayOrEmpty("package");
    final List<TomlTable> tables = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      tables.add(array.getTable(index));
    }
    final Map<String, List<PackageId>> byName = new LinkedHashMap<>();
    final Map<PackageId, TomlTable> sourced = new LinkedHashMap<>();
    final Set<String> local = new HashSet<>();
    for (final TomlTable table : tables) {
      final PackageId id = id(table);
      if (table.getString("source") != null) {
        sourced.put(id, table);
        byName.computeIfAbsent(normalize(id.name()), key -> new ArrayList<>()).add(id);
      } else {
        local.add(normalize(id.name()));
      }
    }
    final Map<PackageId, Locked> packages = new LinkedHashMap<>();
    sourced.forEach((id, table) -> packages.put(id, locked(table, byName)));
    return new CargoLockfile(packages, byName, local);
  }

  private static PackageId id(final TomlTable table) {
    final String name = table.getString("name");
    final String version = table.getString("version");
    if (name == null || version == null) {
      throw new LockfileFormatException("a package has no name or version");
    }
    return crate(name, version);
  }

  private static Locked locked(final TomlTable table, final Map<String, List<PackageId>> byName) {
    final Set<PackageId> requires = new HashSet<>();
    final TomlArray dependencies = table.getArrayOrEmpty("dependencies");
    for (int index = 0; index < dependencies.size(); index++) {
      requires.addAll(resolve(dependencies.getString(index), byName));
    }
    return new Locked(!table.getString("source").startsWith("git+"), requires);
  }

  /** Resolves {@code name}, {@code name version} or {@code name version (source)}. */
  private static List<PackageId> resolve(
      final String entry,
      final Map<String,
      List<PackageId>> byName
  ) {
    final String[] parts = entry.split(" ");
    final List<PackageId> candidates = byName.getOrDefault(normalize(parts[0]), List.of());
    if (parts.length < 2) {
      return candidates;
    }
    return candidates.stream().filter(id -> id.version().equals(parts[1])).toList();
  }
}
