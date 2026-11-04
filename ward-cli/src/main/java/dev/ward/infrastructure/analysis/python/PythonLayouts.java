package dev.ward.infrastructure.analysis.python;

import dev.ward.analysis.domain.python.PythonProjectLayout;
import dev.ward.infrastructure.project.python.PythonProjectInspection;
import dev.ward.infrastructure.project.python.PythonWorkspaces;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.tomlj.TomlTable;

/**
 * Reads the source roots of a Python project from its {@code pyproject.toml}, without running any
 * tool. A uv workspace contributes the roots of its members, and a project contributes the roots of
 * its Poetry {@code packages}, else its {@code src/} directory, else the project directory itself.
 */
public final class PythonLayouts {

  private static final String UV_MEMBERS = "tool.uv.workspace.members";
  private static final String POETRY_PACKAGES = "tool.poetry.packages";
  private static final String FROM = "from";
  private static final String SRC = "src";
  private static final String SEPARATOR = "/";
  private static final String WILDCARDS = "*?[{";
  private static final String GLOB_PREFIX = "^\\./";
  private static final String GLOB_SUFFIX = "/$";
  private static final Logger LOGGER = Logger.getLogger(PythonLayouts.class.getName());

  private PythonLayouts() {}

  /**
   * Reads the source roots of the project at {@code projectRoot}. The roots are relative to that
   * directory and end with a separator, except the project root itself, which is the empty string.
   *
   * @param projectRoot the root of the project
   * @return the layout of the project
   */
  public static PythonProjectLayout read(final Path projectRoot) {
    final TomlTable pyproject = PythonProjectInspection.pyproject(projectRoot);
    final List<String> members = globs(pyproject, UV_MEMBERS);
    final Set<String> roots = new TreeSet<>();
    if (members.isEmpty()) {
      roots.addAll(rootsOf(projectRoot, projectRoot, pyproject));
    } else {
      for (final Path member : workspaceMembers(projectRoot, members)) {
        roots.addAll(rootsOf(projectRoot, member, PythonProjectInspection.pyproject(member)));
      }
    }
    final List<String> sorted = List.copyOf(roots);
    LOGGER.fine(
        () ->
            "Python layout read: "
                + sorted.size()
                + " source roots, "
                + members.size()
                + " workspace member globs");
    return new PythonProjectLayout(sorted);
  }

  /** The roots one project directory contributes, relative to {@code projectRoot}. */
  private static List<String> rootsOf(
      final Path projectRoot, final Path dir, final TomlTable pyproject) {
    final List<String> packages = poetryFroms(pyproject);
    if (!packages.isEmpty()) {
      return packages.stream().map(from -> relativeRoot(projectRoot, dir.resolve(from))).toList();
    }
    if (Files.isDirectory(dir.resolve(SRC))) {
      return List.of(relativeRoot(projectRoot, dir.resolve(SRC)));
    }
    return List.of(relativeRoot(projectRoot, dir));
  }

  /** The {@code from} directories of Poetry's {@code packages}; an entry without one is the dir. */
  private static List<String> poetryFroms(final TomlTable pyproject) {
    return pyproject.getArrayOrEmpty(POETRY_PACKAGES).toList().stream()
        .filter(TomlTable.class::isInstance)
        .map(TomlTable.class::cast)
        .map(table -> table.getString(FROM))
        .map(from -> from == null ? "" : from)
        .toList();
  }

  /** The workspace members that uv would accept: listed in the globs, with a pyproject.toml. */
  private static List<Path> workspaceMembers(final Path projectRoot, final List<String> members) {
    final Set<Path> found = new TreeSet<>();
    for (final String glob : members) {
      for (final Path candidate : expand(projectRoot, glob)) {
        if (PythonWorkspaces.isMember(projectRoot, candidate)) {
          found.add(candidate);
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * The directories a members glob names, expanded one path segment at a time.
   *
   * <p>shortcut: a {@code **} segment matches one directory level only, upgrade when a workspace
   * uses it.
   */
  private static List<Path> expand(final Path projectRoot, final String glob) {
    final String trimmed = glob.replaceFirst(GLOB_PREFIX, "").replaceFirst(GLOB_SUFFIX, "");
    List<Path> current = List.of(projectRoot);
    for (final String segment : trimmed.split(SEPARATOR, -1)) {
      current =
          current.stream().flatMap(dir -> children(dir, segment).stream()).distinct().toList();
    }
    return current;
  }

  private static List<Path> children(final Path dir, final String segment) {
    if (!containsWildcard(segment)) {
      final Path child = dir.resolve(segment);
      return Files.isDirectory(child) ? List.of(child) : List.of();
    }
    final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + segment);
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(Files::isDirectory)
          .filter(entry -> isMemberName(entry, matcher))
          .toList();
    } catch (IOException exception) {
      LOGGER.fine("Python workspace directory cannot be listed; its members are skipped");
      return List.of();
    }
  }

  /** Whether a directory's name matches the glob segment and is not hidden. */
  private static boolean isMemberName(final Path entry, final PathMatcher matcher) {
    final Path name = entry.getFileName();
    return name != null && !name.toString().startsWith(".") && matcher.matches(name);
  }

  private static boolean containsWildcard(final String segment) {
    return segment.chars().anyMatch(character -> WILDCARDS.indexOf(character) >= 0);
  }

  private static List<String> globs(final TomlTable pyproject, final String key) {
    return pyproject.getArrayOrEmpty(key).toList().stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
  }

  /** The root string of {@code dir} relative to {@code projectRoot}, with a trailing separator. */
  private static String relativeRoot(final Path projectRoot, final Path dir) {
    final String relative = projectRoot.relativize(dir.normalize()).toString().replace('\\', '/');
    return relative.isEmpty() ? "" : relative + SEPARATOR;
  }
}
