package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Joins and normalizes project-root-relative paths, and finds the file a base path names. */
final class NodePaths {
  /** The extensions a file import may omit, in the order Node and TypeScript try them. */
  static final List<String> EXTENSIONS =
      List.of(".ts", ".tsx", ".d.ts", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs");

  /** The TypeScript sources a JavaScript extension in an import may name instead. */
  private static final List<SubstitutedExtension> SUBSTITUTIONS =
      List.of(
          new SubstitutedExtension(".js", List.of(".ts", ".tsx", ".d.ts")),
          new SubstitutedExtension(".jsx", List.of(".tsx")),
          new SubstitutedExtension(".mjs", List.of(".mts", ".d.mts")),
          new SubstitutedExtension(".cjs", List.of(".cts", ".d.cts")));

  private static final String INDEX = "/index";
  private static final String SEPARATOR = "/";
  private static final String CURRENT = ".";
  private static final String PARENT = "..";

  private NodePaths() {}

  /** Returns the directory of a file path, or the root when the path has no directory. */
  static String parent(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  /**
   * Joins {@code relative} onto {@code directory} and normalizes {@code .} and {@code ..}. Returns
   * empty when the result would leave the project root.
   */
  static Optional<String> join(final String directory, final String relative) {
    final List<String> segments = new ArrayList<>();
    for (final String part : (directory + SEPARATOR + relative).split(SEPARATOR)) {
      if (part.isEmpty() || CURRENT.equals(part)) {
        continue;
      }
      if (PARENT.equals(part)) {
        if (segments.isEmpty()) {
          return Optional.empty();
        }
        segments.remove(segments.size() - 1);
      } else {
        segments.add(part);
      }
    }
    return Optional.of(String.join(SEPARATOR, segments));
  }

  /**
   * Returns the file that {@code base} names: the file itself, then the file with each extension,
   * then the {@code index} file of a directory of that name. A JavaScript extension also names the
   * TypeScript sources it compiles from, after the exact file has been tried.
   */
  static Optional<String> file(final Set<String> files, final String base) {
    if (files.contains(base)) {
      return Optional.of(base);
    }
    for (final String extension : EXTENSIONS) {
      if (files.contains(base + extension)) {
        return Optional.of(base + extension);
      }
    }
    for (final String extension : EXTENSIONS) {
      if (files.contains(base + INDEX + extension)) {
        return Optional.of(base + INDEX + extension);
      }
    }
    return substituted(files, base);
  }

  private static Optional<String> substituted(final Set<String> files, final String base) {
    for (final SubstitutedExtension substitution : SUBSTITUTIONS) {
      if (base.endsWith(substitution.from())) {
        final String stem = base.substring(0, base.length() - substitution.from().length());
        for (final String to : substitution.to()) {
          if (files.contains(stem + to)) {
            return Optional.of(stem + to);
          }
        }
      }
    }
    return Optional.empty();
  }

  /** A JavaScript extension and the TypeScript extensions an import of it may resolve to. */
  private record SubstitutedExtension(String from, List<String> to) {}
}
