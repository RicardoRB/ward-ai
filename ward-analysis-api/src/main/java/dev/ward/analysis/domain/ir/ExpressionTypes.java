package dev.ward.analysis.domain.ir;

import java.util.Map;
import java.util.Objects;

/**
 * The static types a type resolver found for expression spans of one source unit.
 *
 * <p>Spans the resolver could not answer are absent from {@code types}; they are not errors.
 *
 * @param types the fully qualified type name of each answered span
 */
public record ExpressionTypes(Map<Span, String> types) {

  /**
   * Copies the map so the record cannot change after construction.
   *
   * @throws NullPointerException if the map or one of its keys or values is missing
   */
  public ExpressionTypes {
    Objects.requireNonNull(types, "types");
    types = Map.copyOf(types);
  }

  /** Returns the answer that resolves no span. */
  public static ExpressionTypes none() {
    return new ExpressionTypes(Map.of());
  }
}
