package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A reference from one source unit to another, such as an import, as the source spells it. A
 * resolver turns it into a file.
 *
 * @param specifier the referenced name exactly as written
 * @param span where the reference is in the source
 */
public record Reference(String specifier, Span span) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if the specifier or span is missing
   */
  public Reference {
    Objects.requireNonNull(specifier, "specifier");
    Objects.requireNonNull(span, "span");
  }
}
