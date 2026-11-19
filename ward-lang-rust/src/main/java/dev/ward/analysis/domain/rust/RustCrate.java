package dev.ward.analysis.domain.rust;

import java.util.Objects;

/**
 * One crate of a Cargo project.
 *
 * @param name the name other crates use in a path: the package name with {@code -} as {@code _}
 * @param sourceDirectory the project-root-relative directory that holds {@code lib.rs} or {@code
 *     main.rs}, with {@code /} separators
 */
public record RustCrate(String name, String sourceDirectory) {
  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public RustCrate {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sourceDirectory, "sourceDirectory");
  }
}
