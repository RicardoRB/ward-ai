package dev.ward.domain.license;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.project.Project;

/**
 * Port that reads the license a package declares from local files only: never from the network,
 * never by running the project's build tool, package manager or code. One adapter exists per
 * ecosystem.
 */
@FunctionalInterface
public interface LicenseMetadata {
  /**
   * Returns the license {@code dependency} declares, or why there is none.
   *
   * @param project the project the inventory belongs to
   * @param modulePath the module or workspace member that lists the package, empty for the root
   * @param dependency the package to look up
   */
  LicenseLookup lookup(Project project, String modulePath, Dependency dependency);
}
