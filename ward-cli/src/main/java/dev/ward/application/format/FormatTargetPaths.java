package dev.ward.application.format;

import dev.ward.domain.config.WardException;
import dev.ward.domain.format.FormatTarget;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Narrows the selected files to the paths the user named, relative to the project root. */
final class FormatTargetPaths {
  private static final String SEPARATOR = "/";

  private FormatTargetPaths() {}

  /**
   * Returns the selected files that the paths cover, sorted by path. A path that covers no selected
   * file is an error.
   *
   * @throws WardException if a path lies outside the project or covers no formattable file
   */
  static List<FormatTarget> select(
      final Path root, final List<FormatTarget> all, final List<Path> paths) {
    final Path absoluteRoot = root.toAbsolutePath().normalize();
    final Map<String, FormatTarget> chosen = new TreeMap<>();
    for (final Path path : paths) {
      final String wanted = relative(absoluteRoot, path);
      final List<FormatTarget> matches =
          all.stream().filter(target -> covers(wanted, target.relativePath())).toList();
      if (matches.isEmpty()) {
        throw new WardException("No formattable file at " + wanted + ".");
      }
      matches.forEach(target -> chosen.put(target.relativePath(), target));
    }
    return List.copyOf(chosen.values());
  }

  /** Resolves a path against the project root, as if the command ran there. */
  private static String relative(final Path absoluteRoot, final Path path) {
    final Path absolute = absoluteRoot.resolve(path).normalize();
    if (!absolute.startsWith(absoluteRoot)) {
      throw new WardException(path + " is outside the project.");
    }
    return absoluteRoot.relativize(absolute).toString().replace('\\', '/');
  }

  private static boolean covers(final String wanted, final String file) {
    return wanted.isEmpty() || file.equals(wanted) || file.startsWith(wanted + SEPARATOR);
  }
}
