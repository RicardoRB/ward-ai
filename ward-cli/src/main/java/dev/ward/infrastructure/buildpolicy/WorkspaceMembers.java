package dev.ward.infrastructure.buildpolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Expands workspace member patterns of one directory level, such as {@code packages/*}. */
public final class WorkspaceMembers {
  private static final String STAR = "/*";

  private WorkspaceMembers() {}

  /**
   * Returns the project-relative member directories that {@code patterns} name inside {@code dir},
   * keeping those that contain {@code marker}. Only a literal directory or a trailing {@code /*} is
   * expanded.
   */
  public static List<String> expand(
      final Path root, final String dir, final List<String> patterns, final String marker) {
    final List<String> members = new ArrayList<>();
    for (final String pattern : patterns) {
      if (pattern.endsWith(STAR)) {
        members.addAll(children(root, dir, pattern.substring(0, pattern.length() - 2), marker));
      } else if (Files.isRegularFile(
          root.resolve(BuildPaths.relative(dir, pattern)).resolve(marker))) {
        members.add(BuildPaths.relative(dir, pattern));
      }
    }
    return members;
  }

  private static List<String> children(
      final Path root, final String dir, final String parent, final String marker) {
    final Path base = root.resolve(BuildPaths.relative(dir, parent));
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(base)) {
      return entries
          .filter(entry -> Files.isRegularFile(entry.resolve(marker)))
          .map(entry -> BuildPaths.relative(dir, parent + "/" + entry.getFileName()))
          .sorted()
          .toList();
    } catch (final IOException exception) {
      return List.of();
    }
  }
}
