package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A named declaration in one source unit, exported or declared there.
 *
 * @param name the name as the source spells it
 * @param span where the declaration is in the source
 * @param kind the language's word for what is declared, such as {@code function}, or empty when the
 *     summarizer does not say
 */
public record Symbol(String name, Span span, String kind) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if the name, span or kind is missing
   */
  public Symbol {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(kind, "kind");
  }

  /** Creates a symbol of no stated kind. */
  public Symbol(final String name, final Span span) {
    this(name, span, "");
  }
}
