package dev.ward.analysis.domain.python;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The source roots of a Python project. A file's module name is its path below the deepest root
 * that contains it, so {@code src/shop/price.py} under root {@code src/} is module {@code
 * shop.price}. A file under no root has no module name.
 *
 * @param sourceRoots the roots, each a directory path ending in {@code /}, or the empty string for
 *     the project root
 */
public record PythonProjectLayout(List<String> sourceRoots) {

  private static final String SEPARATOR = "/";
  private static final String PYTHON_SUFFIX = ".py";
  private static final String INIT = "__init__";
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");

  /** Copies the roots and checks that each ends with a separator, or is the project root. */
  public PythonProjectLayout {
    sourceRoots = List.copyOf(sourceRoots);
    for (final String root : sourceRoots) {
      if (!root.isEmpty() && !root.endsWith(SEPARATOR)) {
        throw new IllegalArgumentException("source root must end with /: " + root);
      }
    }
  }

  /** The module of a file, or empty when no root contains it or the path is not a module name. */
  Optional<PythonModule> moduleOf(final String path) {
    final Optional<String> root = deepestRootOf(path);
    if (root.isEmpty() || !path.endsWith(PYTHON_SUFFIX)) {
      return Optional.empty();
    }
    final String relative =
        path.substring(root.get().length(), path.length() - PYTHON_SUFFIX.length());
    final boolean pkg = INIT.equals(relative) || relative.endsWith(SEPARATOR + INIT);
    final String dotted = pkg ? parentOf(relative) : relative;
    return isModuleName(dotted)
        ? Optional.of(new PythonModule(dotted.replace(SEPARATOR, "."), pkg))
        : Optional.empty();
  }

  private Optional<String> deepestRootOf(final String path) {
    final List<String> containing = new ArrayList<>();
    for (final String root : sourceRoots) {
      if (path.startsWith(root)) {
        containing.add(root);
      }
    }
    return containing.stream().max((left, right) -> Integer.compare(left.length(), right.length()));
  }

  /** The path without its last segment, so {@code shop/__init__} is {@code shop}. */
  private static String parentOf(final String relative) {
    final int slash = relative.lastIndexOf(SEPARATOR);
    return slash < 0 ? "" : relative.substring(0, slash);
  }

  /** Every segment is an identifier, so a directory such as {@code my-tool} is not a module. */
  private static boolean isModuleName(final String dotted) {
    if (dotted.isEmpty()) {
      return false;
    }
    for (final String segment : dotted.split(SEPARATOR, -1)) {
      if (!IDENTIFIER.matcher(segment).matches()) {
        return false;
      }
    }
    return true;
  }
}
