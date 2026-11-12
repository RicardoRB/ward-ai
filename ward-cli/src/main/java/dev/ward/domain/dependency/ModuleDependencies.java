package dev.ward.domain.dependency;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The packages one module resolves.
 *
 * @param modulePath the module's path relative to the project root with {@code /}; empty for the
 *     root
 * @param dependencies one entry per {@link PackageId}, ordered by ecosystem, name, then version
 */
public record ModuleDependencies(String modulePath, List<Dependency> dependencies) {
  /**
   * Sorts and copies the entries.
   *
   * @throws IllegalArgumentException if two entries have the same package identity
   */
  public ModuleDependencies {
    dependencies = dependencies.stream().sorted(Comparator.comparing(Dependency::id)).toList();
    final Set<PackageId> seen = new HashSet<>();
    for (final Dependency dependency : dependencies) {
      if (!seen.add(dependency.id())) {
        throw new IllegalArgumentException(
            "Duplicate dependency " + dependency.id() + " in module '" + modulePath + "'");
      }
    }
  }

  /** Returns the entries, ordered by ecosystem, name, then version; a copy-safe immutable list. */
  @Override
  public List<Dependency> dependencies() {
    return List.copyOf(dependencies);
  }
}
