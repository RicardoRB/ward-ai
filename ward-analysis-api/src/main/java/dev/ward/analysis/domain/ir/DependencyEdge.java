package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A resolved dependency: the file {@code from} depends on the file {@code to} through a reference.
 *
 * @param from project-root-relative path of the referencing file
 * @param to project-root-relative path of the referenced file
 * @param reference the reference that resolved to {@code to}
 */
public record DependencyEdge(String from, String to, Reference reference) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public DependencyEdge {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(reference, "reference");
  }
}
