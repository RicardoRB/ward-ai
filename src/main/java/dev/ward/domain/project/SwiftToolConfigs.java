package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.Optional;

/** Finds the configs a Swift project keeps for the tools of its gates. */
@FunctionalInterface
public interface SwiftToolConfigs {
  /**
   * Returns the config named {@code fileName} in {@code root} that Ward did not write, or empty
   * when there is none. A {@code .swiftlint.yml} that Ward wrote does not count; a {@code
   * .swift-format} always does, because its JSON cannot carry Ward's marker.
   */
  Optional<Path> userConfig(Path root, String fileName);
}
