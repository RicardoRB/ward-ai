package dev.ward.domain.buildpolicy;

import java.nio.file.Path;

/** Reads the build files of the project root and of the modules it lists, and nothing else. */
@FunctionalInterface
public interface BuildFiles {
  /**
   * Returns what the build files under {@code root} declare.
   *
   * @throws BuildFileException if a build file cannot be read or parsed
   */
  BuildModel read(Path root);
}
