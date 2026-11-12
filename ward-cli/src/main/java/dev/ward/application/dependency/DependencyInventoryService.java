package dev.ward.application.dependency;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.DependencySources;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * The entry point of the dependency inventory: picks the source of the project's build tool and
 * returns what it resolves. Vulnerability, license, SBOM and policy checks inject this service and
 * never name an adapter.
 *
 * <p>The inventory is complete or the call fails: a project that cannot be resolved (missing, stale
 * or unreadable lockfile, a failing build tool, an unsupported build tool) is a {@link
 * dev.ward.domain.dependency.DependencyInventoryException}, an execution error that names the cause
 * and the command that fixes it. An empty inventory is only ever the result of a source that states
 * the project has no dependencies. Nothing is written to standard output; diagnostics are JUL
 * records on standard error.
 */
public final class DependencyInventoryService {
  private static final Logger LOGGER = Logger.getLogger(DependencyInventoryService.class.getName());

  private final Map<BuildTool, DependencySource> readers;

  /** Creates the service over the sources by build tool. */
  @Inject
  public DependencyInventoryService(final Map<BuildTool, DependencySource> readers) {
    this.readers = Map.copyOf(readers);
  }

  /**
   * Returns the packages {@code project} resolves, one list per module or workspace member.
   *
   * @throws dev.ward.domain.dependency.DependencyInventoryException if it cannot be resolved
   */
  public DependencyInventory inventory(final Project project) {
    LOGGER.log(Level.FINE, () -> "event=dependency-inventory-start tool=" + project.buildTool());
    final DependencyInventory inventory =
        DependencySources.forBuildTool(project.buildTool(), readers).read(project);
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-inventory-done tool="
                + project.buildTool()
                + " modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount());
    return inventory;
  }
}
