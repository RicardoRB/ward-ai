package dev.ward.domain.project;

import java.util.Optional;

/**
 * Port that reads the name a project declares in its build file, from local files only. One adapter
 * exists per toolchain.
 */
@FunctionalInterface
public interface ProjectNameReader {
  /** Returns the name the build file in {@code project} declares, or empty when it has none. */
  Optional<String> read(Project project);
}
