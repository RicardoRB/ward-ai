package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A named declaration in one source unit, exported or declared there.
 *
 * @param name the name as the source spells it
 * @param span where the declaration is in the source
 */
public record Symbol(String name, Span span) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if the name or span is missing
   */
  public Symbol {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(span, "span");
  }
}
