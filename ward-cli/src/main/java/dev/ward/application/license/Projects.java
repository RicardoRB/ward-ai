package dev.ward.application.license;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import java.nio.file.Path;

/** Finds the project that {@code ward sbom} and {@code ward licenses} describe. */
final class Projects {
  private Projects() {}

  /** Returns the project containing {@code directory}. */
  static Project detect(final ProjectDetector detector, final Path directory) {
    return detector
        .detect(directory)
        .orElseThrow(() -> new WardException("No supported project detected in " + directory));
  }
}
