package dev.ward.domain.project.node;

import java.nio.file.Path;
import java.util.Optional;

/** Finds the configs a Node project keeps for the tools of its gates. */
@FunctionalInterface
public interface NodeToolConfigs {
  /**
   * Returns the config of {@code gate}'s tool in {@code root} that Ward did not write, or empty
   * when there is none. A Jest config kept in {@code package.json} returns {@code package.json}.
   */
  Optional<Path> userConfig(Path root, String gate);

  /**
   * Returns whether the project's {@code angular.json} sets a line coverage threshold, which the
   * {@code ng-test} gate enforces. Ward does not write that file, so it reports a missing threshold
   * instead of adding one.
   */
  default boolean angularCoverageThresholdsSet(final Path root) {
    return false;
  }
}
