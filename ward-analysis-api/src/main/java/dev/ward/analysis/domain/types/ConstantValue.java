package dev.ward.analysis.domain.types;

import java.util.Objects;

/**
 * A compile-time constant.
 *
 * @param value a {@code String}, {@code Boolean}, {@code Long} or {@code Double}; characters are
 *     strings and every integral value is a {@code Long}
 */
public record ConstantValue(Object value) {

  /**
   * Checks the kind of value.
   *
   * @throws NullPointerException if the value is missing
   * @throws IllegalArgumentException if it is not one of the allowed kinds
   */
  public ConstantValue {
    Objects.requireNonNull(value, "value");
    if (!(value instanceof String
        || value instanceof Boolean
        || value instanceof Long
        || value instanceof Double)) {
      throw new IllegalArgumentException("unsupported constant");
    }
  }
}
