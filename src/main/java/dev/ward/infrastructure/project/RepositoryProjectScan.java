package dev.ward.infrastructure.project;

import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Walks a repository for project roots, skipping dependencies and grouping build modules. */
final class RepositoryProjectScan {
  private static final Set<String> SKIPPED =
      Set.of("node_modules", "target", "build", "dist", "out", "Pods", "Carthage", "DerivedData");
  private static final List<String> SKIPPED_BUNDLES =
      List.of(".xcodeproj", ".xcworkspace", ".xcassets");

  private RepositoryProjectScan() {}

  /** Returns the projects at most {@code depth} directories below {@code root}, modules grouped. */
  static List<DiscoveredProject> scan(final Path root, final int depth) {
    final List<Project> candidates = candidates(root, depth);
    final List<Project> projects =
        candidates.stream().filter(project -> !isModule(project, candidates)).toList();
    return projects.stream()
        .sorted(
            Comparator.comparing(Project::root).thenComparing(project -> project.language().name()))
        .map(project -> new DiscoveredProject(project, moduleCount(project, candidates, projects)))
        .toList();
  }

  private static List<Project> candidates(final Path root, final int depth) {
    final List<Project> found = new ArrayList<>();
    try {
      Files.walkFileTree(
          root,
          EnumSet.noneOf(FileVisitOption.class),
          depth,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path dir, final BasicFileAttributes attributes) {
              if (!dir.equals(root) && skipped(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              found.addAll(projectsAt(dir));
              return FileVisitResult.CONTINUE;
            }

            /** Directories at the depth limit arrive here instead of in preVisitDirectory. */
            @Override
            public FileVisitResult visitFile(
                final Path file, final BasicFileAttributes attributes) {
              if (attributes.isDirectory() && !skipped(file)) {
                found.addAll(projectsAt(file));
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException failure) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Could not scan project roots: " + e.getMessage(), e);
    }
    return found.stream().distinct().toList();
  }

  static boolean skipped(final Path dir) {
    final Path name = dir.getFileName();
    return name != null && skipped(name.toString());
  }

  private static boolean skipped(final String name) {
    return name.startsWith(".")
        || SKIPPED.contains(name)
        || SKIPPED_BUNDLES.stream().anyMatch(name::endsWith);
  }

  /**
   * Returns the projects rooted at {@code dir}: the one {@link
   * FileSystemProjectInspection#projectAt} picks, plus a Python project when that one is Node and
   * the directory also holds a Python marker.
   */
  private static List<Project> projectsAt(final Path dir) {
    final Optional<Project> project = FileSystemProjectInspection.projectAt(dir);
    if (project.isPresent()
        && project.get().toolchain() == Toolchain.NODE
        && PythonProjectInspection.hasMarker(dir)) {
      return List.of(project.get(), PythonProjectInspection.project(dir));
    }
    return project.stream().toList();
  }

  private static boolean isModule(final Project project, final List<Project> candidates) {
    return candidates.stream()
        .anyMatch(other -> BuildModules.isModuleOf(other.root(), project.root()));
  }

  /** Counts grouped modules whose nearest kept project is {@code project}. */
  private static int moduleCount(
      final Project project, final List<Project> candidates, final List<Project> projects) {
    return (int)
        candidates.stream()
            .filter(candidate -> !projects.contains(candidate))
            .filter(candidate -> owner(candidate, projects).filter(project::equals).isPresent())
            .count();
  }

  private static Optional<Project> owner(final Project module, final List<Project> projects) {
    return projects.stream()
        .filter(project -> module.root().startsWith(project.root()))
        .max(Comparator.comparingInt(project -> project.root().getNameCount()));
  }
}
