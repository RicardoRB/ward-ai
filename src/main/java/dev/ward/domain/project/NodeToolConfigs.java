package dev.ward.domain.project;

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
   * Returns the config Ward wrote for {@code gate}'s tool in {@code root}, or empty when there is
   * none. A layered root config is one of these, with its base config beside it.
   */
  default Optional<Path> wardConfig(final Path root, final String gate) {
    return Optional.empty();
  }

  /**
   * Returns the base config that holds a project's own config renamed under a layer, or empty when
   * there is none. Only Ward's layered root config imports or extends it.
   */
  default Optional<Path> baseConfig(final Path root, final String gate) {
    return Optional.empty();
  }

  /**
   * Returns whether the project's {@code angular.json} sets a line coverage threshold, which the
   * {@code ng-test} gate enforces. Ward does not write that file, so it reports a missing threshold
   * instead of adding one.
   */
  default boolean angularCoverageThresholdsSet(final Path root) {
    return false;
  }
}
