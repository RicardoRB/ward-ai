package dev.ward.analysis.domain.api;

import java.util.Objects;

/**
 * A parameter of a method or constructor.
 *
 * @param type its declared type
 * @param hasDefault whether the language lets a caller omit it with a default value
 */
public record ApiParameter(ApiTypeRef type, boolean hasDefault) {
  /**
   * Checks the type.
   *
   * @throws NullPointerException if the type is missing
   */
  public ApiParameter {
    Objects.requireNonNull(type, "type");
  }
}
