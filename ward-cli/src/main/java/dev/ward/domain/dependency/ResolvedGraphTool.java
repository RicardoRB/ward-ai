package dev.ward.domain.dependency;

import dev.ward.domain.project.Project;

/** Port for a build tool that resolves a project's dependency graph: Maven or Gradle. */
@FunctionalInterface
public interface ResolvedGraphTool {
  /**
   * Runs the build tool and returns the resolved inventory.
   *
   * @throws DependencyInventoryException when the tool cannot be started, fails, times out, reports
   *     an unresolved dependency or prints output that cannot be read
   */
  DependencyInventory resolve(Project project);
}
