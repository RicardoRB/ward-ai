package dev.ward.infrastructure.project;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Lists the project directories a bounded walk reaches, skipping build output and hidden ones. */
public final class ProjectDirectories {
  /** How far below a project root build modules and their reports are looked for. */
  public static final int MODULE_DEPTH = 4;

  private ProjectDirectories() {}

  /** Returns {@code root} and the directories at most {@link #MODULE_DEPTH} below it. */
  public static List<Path> below(final Path root) {
    final List<Path> directories = new ArrayList<>();
    try {
      Files.walkFileTree(
          root,
          EnumSet.noneOf(FileVisitOption.class),
          MODULE_DEPTH,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path dir, final BasicFileAttributes attributes) {
              if (!dir.equals(root) && RepositoryProjectScan.skipped(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              directories.add(dir);
              return FileVisitResult.CONTINUE;
            }

            /** Directories at the depth limit arrive here instead of in preVisitDirectory. */
            @Override
            public FileVisitResult visitFile(
                final Path file, final BasicFileAttributes attributes) {
              if (attributes.isDirectory() && !RepositoryProjectScan.skipped(file)) {
                directories.add(file);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException failure) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Could not list project directories: " + e.getMessage(), e);
    }
    return directories;
  }
}
