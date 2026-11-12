package dev.ward.domain.dependency;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Everything a project resolves, one list per module or workspace member. Immutable; read it from
 * {@code DependencyInventoryService}, which fails with an execution error instead of returning a
 * partial or empty inventory when the project cannot be resolved.
 *
 * @param modules the root module first, then the other modules ordered by path
 */
public record DependencyInventory(List<ModuleDependencies> modules) {
  /** Orders and copies the modules, the root (empty path) first. */
  public DependencyInventory {
    modules =
        modules.stream().sorted(Comparator.comparing(ModuleDependencies::modulePath)).toList();
  }

  /** Returns every distinct package of every module, ordered, for checks that ignore modules. */
  public SortedSet<PackageId> distinct() {
    final SortedSet<PackageId> ids = new TreeSet<>();
    modules.forEach(module -> module.dependencies().forEach(entry -> ids.add(entry.id())));
    return ids;
  }

  /** Returns the module at {@code modulePath} (empty for the root), if the inventory has one. */
  public Optional<ModuleDependencies> of(final String modulePath) {
    return modules.stream().filter(module -> module.modulePath().equals(modulePath)).findFirst();
  }

  /** Returns the number of entries over all modules, counting a shared package once per module. */
  public int entryCount() {
    return modules.stream().mapToInt(module -> module.dependencies().size()).sum();
  }
}
