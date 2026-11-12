package dev.ward.infrastructure.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** Copies, removes and restores the files that a compatibility matrix adds to a project. */
public final class MatrixFileTrees {
  private MatrixFileTrees() {}

  /** Copies every file of {@code source} into {@code target}, replacing what is there. */
  public static void copy(final Path source, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** Deletes from {@code target} each file that {@code added} holds, undoing {@link #copy}. */
  public static void remove(final Path added, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(added)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        Files.delete(target.resolve(added.relativize(file).toString()));
      }
    }
  }

  /**
   * Undoes {@link #copy} of {@code added} over a project built from {@code fixture}: a file that
   * the fixture has comes back as the fixture wrote it, and a file only {@code added} holds is
   * deleted.
   */
  public static void restore(final Path added, final Path fixture, final Path target)
      throws IOException {
    try (Stream<Path> files = Files.walk(added)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final String relative = added.relativize(file).toString();
        final Path original = fixture.resolve(relative);
        if (Files.exists(original)) {
          Files.copy(original, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
        } else {
          Files.delete(target.resolve(relative));
        }
      }
    }
  }
}
