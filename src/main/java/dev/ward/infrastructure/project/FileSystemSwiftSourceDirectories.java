package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.SwiftSourceDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Lists a Swift project's top-level source directories from the file system. */
public final class FileSystemSwiftSourceDirectories implements SwiftSourceDirectories {
  private static final String PACKAGE = "Package.swift";
  private static final String SWIFT_SUFFIX = ".swift";
  private static final Set<String> EXCLUDED = Set.of(".build", "Pods", "Carthage", "DerivedData");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemSwiftSourceDirectories() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public List<String> of(final Path root) {
    final List<String> directories = new ArrayList<>();
    if (Files.isRegularFile(root.resolve(PACKAGE))) {
      directories.add(PACKAGE);
    }
    directories.addAll(sourceDirectories(root));
    return List.copyOf(directories);
  }

  private static List<String> sourceDirectories(final Path root) {
    try (Stream<Path> entries = Files.list(root)) {
      return entries
          .filter(Files::isDirectory)
          .map(entry -> root.relativize(entry).toString())
          .filter(name -> !name.startsWith(".") && !EXCLUDED.contains(name))
          .filter(name -> hasSwiftFile(root.resolve(name)))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new WardException("Could not list " + root + ": " + e.getMessage(), e);
    }
  }

  private static boolean hasSwiftFile(final Path directory) {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.anyMatch(file -> file.toString().endsWith(SWIFT_SUFFIX));
    } catch (IOException e) {
      throw new WardException("Could not list " + directory + ": " + e.getMessage(), e);
    }
  }
}
