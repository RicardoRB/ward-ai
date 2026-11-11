package dev.ward.application.gate;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineHits;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Matches the entries of coverage reports to the source files of a project, merging the lines of
 * entries that name the same file.
 *
 * @param lines the merged lines of each matched source file, by project-relative path
 * @param namedBy the reports that name each matched source file
 */
record CoverageMatching(Map<String, FileLines> lines, Map<String, Set<Path>> namedBy) {

  private static final String SLASH = "/";

  /**
   * Matches every entry of {@code reports} to one of {@code sources}, the project-relative paths of
   * the source files. An entry that names no source file is dropped.
   */
  static CoverageMatching of(
      final Path root, final List<LineHits> reports, final Set<String> sources) {
    final Map<String, FileLines> lines = new TreeMap<>();
    final Map<String, Set<Path>> namedBy = new HashMap<>();
    for (final LineHits report : reports) {
      final List<String> reportDirectory = directory(root, report.report());
      for (final LineHits.Entry entry : report.entries()) {
        final String source =
            entry.kind() == LineHits.Kind.PATH
                ? byPath(root, entry.name(), reportDirectory, sources)
                : byPackage(entry.name(), reportDirectory, sources);
        if (source != null) {
          lines.merge(source, entry.lines(), FileLines::merge);
          namedBy.computeIfAbsent(source, key -> new TreeSet<>()).add(report.report());
        }
      }
    }
    return new CoverageMatching(lines, namedBy);
  }

  /** The directory of a report relative to the project root, as path segments. */
  private static List<String> directory(final Path root, final Path report) {
    final Path parent = report.toAbsolutePath().normalize().getParent();
    final Path base = root.toAbsolutePath().normalize();
    final List<String> segments = new ArrayList<>();
    if (parent != null && parent.startsWith(base)) {
      base.relativize(parent).forEach(segment -> segments.add(segment.toString()));
    }
    segments.removeIf(String::isEmpty);
    return segments;
  }

  /** An lcov path: absolute, or against the root and then each directory from the report up. */
  private static String byPath(
      final Path root,
      final String name,
      final List<String> reportDirectory,
      final Set<String> sources) {
    final Path path = Path.of(name);
    if (path.isAbsolute()) {
      final Path base = root.toAbsolutePath().normalize();
      final Path normal = path.normalize();
      return normal.startsWith(base) ? known(base.relativize(normal).toString(), sources) : null;
    }
    final String direct = known(path.normalize().toString(), sources);
    if (direct != null) {
      return direct;
    }
    for (int depth = reportDirectory.size(); depth > 0; depth--) {
      final Path prefix = Path.of(String.join(SLASH, reportDirectory.subList(0, depth)));
      final String found = known(prefix.resolve(path).normalize().toString(), sources);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static String known(final String relative, final Set<String> sources) {
    final String unified = relative.replace('\\', '/');
    return sources.contains(unified) ? unified : null;
  }

  /** A JaCoCo package and file: the source whose path ends with it, nearest to the report. */
  private static String byPackage(
      final String name, final List<String> reportDirectory, final Set<String> sources) {
    String best = null;
    int bestShared = -1;
    for (final String source : new TreeSet<>(sources)) {
      if (source.equals(name) || source.endsWith(SLASH + name)) {
        final int shared = shared(reportDirectory, source);
        if (shared > bestShared) {
          best = source;
          bestShared = shared;
        }
      }
    }
    return best;
  }

  /** The number of leading directories that the report's directory and the source share. */
  private static int shared(final List<String> reportDirectory, final String source) {
    final String[] segments = source.split(SLASH);
    int count = 0;
    while (count < reportDirectory.size()
        && count < segments.length - 1
        && reportDirectory.get(count).equals(segments[count])) {
      count++;
    }
    return count;
  }
}
