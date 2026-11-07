package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A field of a class.
 *
 * @param name the field name
 * @param access the access flags
 * @param type the declared type
 * @param constant the value of a constant variable: a {@code String}, {@code Boolean}, {@code Long}
 *     or {@code Double}
 * @param annotations the qualified names of its annotations
 */
public record JvmField(
    String name, int access, JType type, Optional<Object> constant, List<String> annotations) {
  /**
   * Copies the annotations.
   *
   * @throws NullPointerException if a field is missing
   */
  public JvmField {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(constant, "constant");
    annotations = List.copyOf(annotations);
  }

  /** Returns whether the field is static. */
  public boolean isStatic() {
    return (access & Flags.STATIC) != 0;
  }
}
