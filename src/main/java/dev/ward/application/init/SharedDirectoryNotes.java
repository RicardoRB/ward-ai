package dev.ward.application.init;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;

/**
 * The plan note for a directory that holds a Node project and a Python project: init configures the
 * Node project, and one {@code ward.toml} serves one project per directory.
 */
final class SharedDirectoryNotes {
  private static final String NOTE =
      "A Python project shares this directory with the Node project, so init did not configure it."
          + " One ward.toml serves one project per directory.";

  private SharedDirectoryNotes() {}

  /** Returns the note when {@code project} is Node and a Python project shares its root. */
  static List<String> of(final ProjectDetector detector, final Project project) {
    if (project.toolchain() != Toolchain.NODE) {
      return List.of();
    }
    final boolean pythonHere =
        detector.discover(project.root()).stream()
            .map(DiscoveredProject::project)
            .anyMatch(
                other ->
                    other.toolchain() == Toolchain.PYTHON && other.root().equals(project.root()));
    return pythonHere ? List.of(NOTE) : List.of();
  }
}
