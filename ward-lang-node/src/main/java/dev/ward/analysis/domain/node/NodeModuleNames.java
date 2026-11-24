package dev.ward.analysis.domain.node;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * The name of the module of a file, which is the owner of its function bodies, and the module that a
 * relative specifier written in a file names. Both are paths without the script extension, and an
 * {@code index} file names its folder, so the owner of {@code lib/util.ts} and the module that
 * {@code import ... from '../lib/util.js'} resolves to are one string. A specifier that is not
 * relative (a package, or an alias) is kept as written.
 */
final class NodeModuleNames {
  private static final Pattern EXTENSION = Pattern.compile("\\.(?:[cm]?[jt]s|[jt]sx)$");
  private static final Pattern INDEX = Pattern.compile("(?:^|/)index$");
  private static final String SEPARATOR = "/";
  private static final String HERE = ".";
  private static final String UP = "..";

  private NodeModuleNames() {}

  /** The module name of the file at {@code path}. */
  static String of(final String path) {
    final String bare = EXTENSION.matcher(path.replace('\\', '/')).replaceFirst("");
    return INDEX.matcher(bare).replaceFirst("");
  }

  /** The folder of the file at {@code path}, or empty at the root. */
  static String directoryOf(final String path) {
    final String slashed = path.replace('\\', '/');
    final int cut = slashed.lastIndexOf('/');
    return cut < 0 ? "" : slashed.substring(0, cut);
  }

  /** The module that {@code specifier} names in a file of {@code directory}. */
  static String resolve(final String directory, final String specifier) {
    if (!specifier.startsWith("./") && !specifier.startsWith("../")) {
      return specifier;
    }
    final Deque<String> parts = new ArrayDeque<>();
    for (final String part : (directory + SEPARATOR + specifier).split(SEPARATOR)) {
      if (UP.equals(part)) {
        if (parts.isEmpty()) {
          return specifier;
        }
        parts.removeLast();
      } else if (!part.isEmpty() && !HERE.equals(part)) {
        parts.addLast(part);
      }
    }
    return of(String.join(SEPARATOR, parts));
  }
}
