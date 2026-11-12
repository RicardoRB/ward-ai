package dev.ward.domain.dependency;

import dev.ward.domain.project.Project;

/** Reads the inventory of one kind of project: a lockfile reader or a build-tool resolution. */
@FunctionalInterface
public interface DependencySource {
  /**
   * Returns the inventory of {@code project}.
   *
   * @throws DependencyInventoryException when the project cannot be resolved; a partial inventory
   *     is never returned
   */
  DependencyInventory read(Project project);
}
