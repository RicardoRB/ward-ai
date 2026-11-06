package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Objects;

/**
 * A resolved type.
 *
 * @param name the qualified name without type arguments or array brackets; for TypeScript the type
 *     as the compiler prints it
 * @param arguments the type arguments, empty when none or when one did not resolve
 * @param arrayDepth the number of array dimensions
 * @param primitive whether the type is a primitive, including {@code void}
 */
public record TypeRef(String name, List<TypeRef> arguments, int arrayDepth, boolean primitive) {

  /**
   * Copies the arguments.
   *
   * @throws NullPointerException if the name or arguments are missing
   * @throws IllegalArgumentException if the name is blank or the depth is negative
   */
  public TypeRef {
    Objects.requireNonNull(name, "name");
    if (name.isBlank() || arrayDepth < 0) {
      throw new IllegalArgumentException("invalid type reference");
    }
    arguments = List.copyOf(arguments);
  }

  /** Returns the plain reference to the class or interface {@code name}. */
  public static TypeRef of(final String name) {
    return new TypeRef(name, List.of(), 0, false);
  }

  /** Returns the name with its array brackets, as the type-name view of a sidecar shows it. */
  public String display() {
    return name + "[]".repeat(arrayDepth);
  }
}
