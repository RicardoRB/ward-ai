package dev.ward.infrastructure.project;

import dev.ward.domain.project.NodeSources;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Walks a Node package for JavaScript sources, skipping dependencies, build output, projects of
 * other build tools, packages that are not workspace members, and root-level tool configs.
 */
public final class FileSystemNodeSources implements NodeSources {
  private static final Logger LOGGER = Logger.getLogger(FileSystemNodeSources.class.getName());
  private static final Pattern SOURCE = Pattern.compile(".*\\.(js|jsx|mjs|cjs|ts|tsx|mts|cts)$");
  private static final Pattern TOOL_CONFIG =
      Pattern.compile(".*\\.config\\.(js|mjs|cjs|ts|mts|cts)$");
  static final Set<String> OTHER_BUILD_FILES =
      Set.of(
          FileSystemProjectInspection.POM,
          "build.gradle",
          "build.gradle.kts",
          "settings.gradle",
          "settings.gradle.kts",
          "pyproject.toml",
          "setup.py",
          "setup.cfg",
          "Cargo.toml",
          "go.mod",
          "Package.swift");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemNodeSources() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public boolean hasSources(final Path root) {
    return sources(root, false);
  }

  /** Returns whether a source sits below {@code root}, not directly in it. */
  @Override
  public boolean hasSourceTree(final Path root) {
    return sources(root, true);
  }

  private static boolean sources(final Path root, final boolean nestedOnly) {
    final AtomicBoolean found = new AtomicBoolean();
    try {
      Files.walkFileTree(
          root,
          EnumSet.noneOf(FileVisitOption.class),
          FileSystemProjectInspection.MAX_SEARCH_DEPTH,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path dir, final BasicFileAttributes attributes) {
              return dir.equals(root) || !skipped(root, dir)
                  ? FileVisitResult.CONTINUE
                  : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(
                final Path file, final BasicFileAttributes attributes) {
              if (attributes.isRegularFile()
                  && isSource(root, file)
                  && (!nestedOnly || !root.equals(file.getParent()))) {
                found.set(true);
                return FileVisitResult.TERMINATE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException failure) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Could not inspect JavaScript sources in " + root, e);
    }
    LOGGER.log(Level.INFO, () -> "event=node-sources outcome=" + (found.get() ? "found" : "none"));
    return found.get();
  }

  private static boolean skipped(final Path root, final Path dir) {
    final Path name = dir.getFileName();
    return RepositoryProjectScan.skipped(dir)
        || name != null && "coverage".equals(name.toString())
        || OTHER_BUILD_FILES.stream().anyMatch(file -> Files.isRegularFile(dir.resolve(file)))
        || Files.isRegularFile(dir.resolve(NodeProjectInspection.PACKAGE_JSON))
            && !NodeWorkspaces.isMember(root, dir);
  }

  private static boolean isSource(final Path root, final Path file) {
    final String name = String.valueOf(file.getFileName());
    return SOURCE.matcher(name).matches()
        && !(root.equals(file.getParent()) && TOOL_CONFIG.matcher(name).matches());
  }
}
