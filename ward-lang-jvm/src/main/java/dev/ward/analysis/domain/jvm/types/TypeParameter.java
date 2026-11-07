package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Objects;

/**
 * A declared type parameter.
 *
 * @param name the parameter name
 * @param bounds the declared bounds, empty when it is bounded by {@code Object}
 */
public record TypeParameter(String name, List<JType> bounds) {
  /**
   * Copies the bounds.
   *
   * @throws NullPointerException if a field is missing
   */
  public TypeParameter {
    Objects.requireNonNull(name, "name");
    bounds = List.copyOf(bounds);
  }
}
