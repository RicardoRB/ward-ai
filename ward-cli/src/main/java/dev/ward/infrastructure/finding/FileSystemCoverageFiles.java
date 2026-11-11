package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.CoverageFiles;
import dev.ward.infrastructure.project.ProjectDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;

/** Answers the {@code crap} gate's file questions from the file system. */
public final class FileSystemCoverageFiles implements CoverageFiles {

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemCoverageFiles() {}

  @Override
  public List<Path> moduleDirectories(final Path root) {
    return ProjectDirectories.below(root);
  }

  @Override
  public boolean isFile(final Path file) {
    return Files.isRegularFile(file);
  }

  @Override
  public Instant modified(final Path file) {
    try {
      return Files.getLastModifiedTime(file).toInstant();
    } catch (IOException e) {
      return Instant.MIN;
    }
  }
}
