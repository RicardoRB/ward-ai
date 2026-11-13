package dev.ward.analysis.domain.api;

import java.util.List;
import java.util.Objects;

/**
 * A declared type parameter.
 *
 * @param name the name as written
 * @param bounds the declared bounds, empty when it has none
 * @param variance {@code in} or {@code out} for a Kotlin declaration-site variance, else empty
 */
public record ApiTypeParameter(String name, List<ApiTypeRef> bounds, String variance) {
  /**
   * Copies the bounds.
   *
   * @throws NullPointerException if a field is missing
   */
  public ApiTypeParameter {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(variance, "variance");
    bounds = List.copyOf(bounds);
  }
}
