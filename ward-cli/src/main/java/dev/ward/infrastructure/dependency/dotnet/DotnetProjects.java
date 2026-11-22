package dev.ward.infrastructure.dependency.dotnet;

import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.dotnet.DotnetProjectInspection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Finds the project files of a .NET project: the ones its solutions list, or those at its root. */
final class DotnetProjects {
  private static final String EXTENSION = ".csproj";

  private DotnetProjects() {}

  /** Returns the {@code .csproj} files of {@code project}, sorted, one per listed directory. */
  static List<Path> projectFiles(final Project project) {
    final Set<Path> directories = new TreeSet<>();
    directories.add(project.root().toAbsolutePath().normalize());
    directories.addAll(DotnetProjectInspection.members(project.root()));
    final Set<Path> files = new TreeSet<>();
    for (final Path directory : directories) {
      files.addAll(csprojIn(directory));
    }
    return List.copyOf(files);
  }

  private static List<Path> csprojIn(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> children = Files.list(directory)) {
      return children
          .filter(Files::isRegularFile)
          .filter(file -> LockFiles.fileName(file).endsWith(EXTENSION))
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }
}
