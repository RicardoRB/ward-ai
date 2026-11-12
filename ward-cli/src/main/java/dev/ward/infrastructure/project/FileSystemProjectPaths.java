package dev.ward.infrastructure.project;

import dev.ward.domain.project.ProjectPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

/** Filesystem-backed answers to whether a project-relative path is a directory or a file. */
public final class FileSystemProjectPaths implements ProjectPaths {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemProjectPaths() {
    // Stateless adapter; Dagger constructs it through this constructor.
  }

  @Override
  public boolean isDirectory(final Path projectRoot, final Path relative) {
    return Files.isDirectory(projectRoot.resolve(relative));
  }

  @Override
  public boolean isRegularFile(final Path projectRoot, final Path relative) {
    return Files.isRegularFile(projectRoot.resolve(relative));
  }
}
