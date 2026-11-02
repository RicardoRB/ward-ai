package dev.ward.infrastructure.project;

import dev.ward.domain.project.DiscoveredProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A temporary Git repository tree for discovery tests. */
public final class ProjectTree {
  private final Path root;

  /** Creates a Git repository at {@code root} for the discovery test. */
  public ProjectTree(final Path root) throws IOException {
    this.root = root;
    Files.createDirectories(root.resolve(".git"));
  }

  /** Returns the absolute, normalized path of {@code relative} in the tree. */
  public Path path(final String relative) {
    return root.resolve(relative).toAbsolutePath().normalize();
  }

  /** Writes {@code content} to {@code relative} in the tree, creating its folders. */
  public void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  /** Returns the root of each discovered project. */
  public static List<Path> roots(final List<DiscoveredProject> projects) {
    return projects.stream().map(discovered -> discovered.project().root()).toList();
  }
}
