package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
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
   * path, without the files that Git ignores. When Git cannot answer, no file is dropped.
   *
   * @throws WardException if a folder cannot be listed or a file cannot be read
   */
  static ProjectFiles read(
      final Path root, final Set<String> names, final CommandExecutor executor) {
    if (names.isEmpty()) {
      return ProjectFiles.empty();
    }
    final Map<String, String> texts = new TreeMap<>();
    try {
      Files.walkFileTree(root, new Collector(root, names, texts));
    } catch (final IOException exception) {
      throw new WardException("Could not read the manifest files under " + root + ".", exception);
    }
    final Set<String> ignored = ignored(root, executor);
    texts.keySet().removeIf(ignored::contains);
    return new ProjectFiles(texts);
  }

  /** The untracked files that Git ignores, relative to {@code root}; empty when Git cannot say. */
  private static Set<String> ignored(final Path root, final CommandExecutor executor) {
    final CommandResult result =
        executor.execute(List.of("git", "ls-files", "-o", "-i", "--exclude-standard", "-z"), root);
    if (result.executionError() != null || result.exitCode() != 0) {
      return Set.of();
    }
    return Set.of(result.stdout().split("\\u0000"));
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
