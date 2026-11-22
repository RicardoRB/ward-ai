package dev.ward.infrastructure.project;

import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.dotnet.DotnetProjectInspection;
import dev.ward.infrastructure.project.python.PythonProjectInspection;
import dev.ward.infrastructure.project.swift.SwiftProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Detects the projects that no fixed marker file names, from the names a directory lists. */
final class ListedFileProjects {
  private ListedFileProjects() {}

  /** Returns the Python requirements, Swift or .NET project in {@code dir}, if there is one. */
  static Optional<Project> project(final Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      final List<String> names =
          files.map(Path::getFileName).filter(Objects::nonNull).map(Path::toString).toList();
      if (names.stream().anyMatch(PythonProjectInspection::isRequirementsFile)) {
        return Optional.of(PythonProjectInspection.project(dir));
      }
      final Optional<Project> swift = SwiftProjectInspection.project(dir, names);
      if (swift.isPresent()) {
        return swift;
      }
      return DotnetProjectInspection.project(dir, names);
    }
  }
}
