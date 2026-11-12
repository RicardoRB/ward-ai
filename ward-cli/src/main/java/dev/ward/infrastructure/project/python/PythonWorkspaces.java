package dev.ward.infrastructure.project.python;

import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.WorkspaceGlobs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.tomlj.TomlTable;

/**
 * Reads uv workspace members and excludes from a root pyproject.toml and matches member
 * directories.
 */
public final class PythonWorkspaces {
  private static final String MEMBERS = "tool.uv.workspace.members";
  private static final String EXCLUDE = "tool.uv.workspace.exclude";

  private PythonWorkspaces() {}

  /**
   * Returns whether {@code candidate} is a uv workspace member of {@code root}: it has a
   * pyproject.toml, is listed in {@code members}, and is not listed in {@code exclude}.
   */
  public static boolean isMember(final Path root, final Path candidate) {
    if (!Files.isRegularFile(candidate.resolve(PythonProjectInspection.PYPROJECT))) {
      return false;
    }
    final TomlTable pyproject = PythonProjectInspection.pyproject(root);
    return WorkspaceGlobs.matchesAny(globs(pyproject, MEMBERS), root, candidate)
        && !WorkspaceGlobs.matchesAny(globs(pyproject, EXCLUDE), root, candidate);
  }

  /**
   * Returns the uv workspace member directories of {@code root}, sorted, not including {@code
   * root}; empty when it is not a workspace root or cannot be read.
   */
  public static List<Path> members(final Path root) {
    if (!Files.isRegularFile(root.resolve(PythonProjectInspection.PYPROJECT))) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root, FileSystemProjectInspection.MAX_SEARCH_DEPTH)) {
      return files
          .filter(Files::isDirectory)
          .filter(dir -> !dir.equals(root) && isMember(root, dir))
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }

  /** Returns the string entries of the array at {@code key}; a missing array is empty. */
  private static List<String> globs(final TomlTable pyproject, final String key) {
    return pyproject.getArrayOrEmpty(key).toList().stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
  }
}
