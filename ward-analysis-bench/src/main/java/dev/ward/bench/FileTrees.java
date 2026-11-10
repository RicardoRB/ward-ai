package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Copies and deletes corpus working trees. Symbolic links are recreated, never followed. */
final class FileTrees {

  private FileTrees() {}

  static void copy(final Path source, final Path target) {
    try {
      Files.createDirectories(target);
      Files.walkFileTree(
          source,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, BasicFileAttributes attrs)
                throws IOException {
              Files.createDirectories(target.resolve(source.relativize(dir)));
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              final Path destination = target.resolve(source.relativize(file));
              if (attrs.isSymbolicLink()) {
                Files.createSymbolicLink(destination, Files.readSymbolicLink(file));
              } else {
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException("Could not copy a corpus tree", e);
    }
  }

  static void delete(final Path root) {
    if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      final List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
      for (final Path path : ordered) {
        Files.delete(path);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not delete a corpus tree", e);
    }
  }
}
