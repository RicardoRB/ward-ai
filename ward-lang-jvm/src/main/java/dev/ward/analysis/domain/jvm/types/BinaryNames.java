package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Builds binary class names from the pieces a source file writes. */
final class BinaryNames {
  private BinaryNames() {}

  /** Joins a package and a name; the default package has no prefix. */
  static String qualified(final String packageName, final String name) {
    return packageName.isEmpty() ? name : packageName + '.' + name;
  }

  /** Returns the simple name of a binary name: the part after the last dot or {@code $}. */
  static String simpleName(final String binaryName) {
    final int cut = Math.max(binaryName.lastIndexOf('.'), binaryName.lastIndexOf('$'));
    return binaryName.substring(cut + 1);
  }

  /** Returns the member class reached from {@code owner} through {@code names}, if each exists. */
  static Optional<String> members(
      final String owner, final List<String> names, final Predicate<String> exists) {
    final StringBuilder current = new StringBuilder(owner);
    for (final String name : names) {
      current.append('$').append(name);
      if (!exists.test(current.toString())) {
        return Optional.empty();
      }
    }
    return Optional.of(current.toString());
  }

  /**
   * Turns a qualified name with dots into a binary name by trying each split into a package and
   * nested classes, longest package first.
   */
  static Optional<String> of(final String dotted, final Predicate<String> exists) {
    final List<String> parts = List.of(dotted.split("\\."));
    for (int classStart = parts.size() - 1; classStart >= 0; classStart--) {
      final String packageName = String.join(".", parts.subList(0, classStart));
      final String classes = String.join("$", parts.subList(classStart, parts.size()));
      final String candidate = qualified(packageName, classes);
      if (exists.test(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
