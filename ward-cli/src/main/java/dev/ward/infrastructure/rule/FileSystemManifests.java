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
import java.util.stream.Stream;

/**
 * Reads the manifest files of a project, such as {@code package.json}, from the working tree. It
 * walks the same folders that source reads walk, so {@code node_modules} and hidden folders are
 * never read.
 */
final class FileSystemManifests {

  private static final String NODE_MODULES = "node_modules";
  private static final String MANIFEST = "package.json";

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
    addInstalled(root, texts);
    return new ProjectFiles(texts);
  }

  /**
   * Adds the {@code package.json} of each installed package directly under {@code node_modules},
   * and of each scoped package, and nothing else under it. Git ignores {@code node_modules}, so
   * these are not filtered.
   */
  private static void addInstalled(final Path root, final Map<String, String> texts) {
    final Path modules = root.resolve(NODE_MODULES);
    if (!Files.isDirectory(modules)) {
      return;
    }
    try (Stream<Path> entries = Files.list(modules)) {
      for (final Path entry : entries.toList()) {
        if (nameOf(entry).startsWith("@")) {
          addScoped(root, entry, texts);
        } else {
          addManifest(root, entry, texts);
        }
      }
    } catch (final IOException exception) {
      throw new WardException(
          "Could not list the installed packages under " + modules + ".", exception);
    }
  }

  private static void addScoped(final Path root, final Path scope, final Map<String, String> texts)
      throws IOException {
    if (!Files.isDirectory(scope)) {
      return;
    }
    try (Stream<Path> packages = Files.list(scope)) {
      for (final Path entry : packages.toList()) {
        addManifest(root, entry, texts);
      }
    }
  }

  private static void addManifest(
      final Path root, final Path directory, final Map<String, String> texts) throws IOException {
    final Path manifest = directory.resolve(MANIFEST);
    if (Files.isRegularFile(manifest)) {
      texts.put(
          root.relativize(manifest).toString().replace('\\', '/'),
          Files.readString(manifest, StandardCharsets.UTF_8));
    }
  }

  private static String nameOf(final Path path) {
    final Path name = path.getFileName();
    return name == null ? "" : name.toString();
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
