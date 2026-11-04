package dev.ward.analysis.domain.jvm;

import java.util.Objects;

/**
 * A source root of a Java module, relative to the project root with {@code /} separators and no
 * trailing separator.
 *
 * @param directory the root directory, empty for the project root itself
 * @param module the module the root belongs to, empty for the project root module
 * @param test whether the root holds test sources
 */
public record JavaSourceRoot(String directory, String module, boolean test) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public JavaSourceRoot {
    Objects.requireNonNull(directory, "directory");
    Objects.requireNonNull(module, "module");
  }

  /** Returns whether the project-relative {@code path} lies under this root. */
  boolean contains(final String path) {
    return directory.isEmpty() || path.startsWith(directory + "/");
  }
}
