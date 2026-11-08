package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the manifest files of a project, such as {@code package.json}, from the working tree. It
 * walks the same folders that source reads walk, so {@code node_modules} and hidden folders are
 * never read.
 */
final class FileSystemManifests {

  private FileSystemManifests() {}

  /**
   * Returns the files under {@code root} whose names are in {@code names}, by project-relative
   * path.
   *
   * @throws WardException if a folder cannot be listed or a file cannot be read
   */
  static ProjectFiles read(final Path root, final Set<String> names) {
    if (names.isEmpty()) {
      return ProjectFiles.empty();
    }
    final Map<String, String> texts = new TreeMap<>();
    try {
      Files.walkFileTree(root, new Collector(root, names, texts));
    } catch (final IOException exception) {
      throw new WardException("Could not read the manifest files under " + root + ".", exception);
    }
    return new ProjectFiles(texts);
  }

  /** Reads each file whose name is wanted, skipping the folders source reads skip. */
  private static final class Collector extends SimpleFileVisitor<Path> {
    private final Path root;
    private final Set<String> names;
    private final Map<String, String> texts;

    Collector(final Path root, final Set<String> names, final Map<String, String> texts) {
      super();
      this.root = root;
      this.names = names;
      this.texts = texts;
    }

    @Override
    public FileVisitResult preVisitDirectory(
        final Path directory, final BasicFileAttributes attributes) {
      final boolean skipped =
          !directory.equals(root) && FileSystemSourceUnits.skipsDirectory(nameOf(directory));
      return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
        throws IOException {
      if (names.contains(nameOf(file)) && attributes.isRegularFile()) {
        texts.put(relative(file), Files.readString(file, StandardCharsets.UTF_8));
      }
      return FileVisitResult.CONTINUE;
    }

    private String relative(final Path file) {
      return root.relativize(file).toString().replace('\\', '/');
    }

    private static String nameOf(final Path path) {
      final Path name = path.getFileName();
      return name == null ? "" : name.toString();
    }
  }
}
