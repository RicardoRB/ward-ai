package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A reference from one source unit to another, such as an import, as the source spells it. A
 * resolver turns it into a file.
 *
 * @param specifier the referenced name exactly as written
 * @param span where the reference is in the source
 * @param typeChecking whether the reference sits under a guard that only type checkers run, such as
 *     Python's {@code if TYPE_CHECKING:}, so that it is not a runtime dependency
 */
public record Reference(String specifier, Span span, boolean typeChecking) {

  /**
   * Creates a reference that is a runtime dependency.
   *
   * @throws NullPointerException if the specifier or span is missing
   */
  public Reference(final String specifier, final Span span) {
    this(specifier, span, false);
  }

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
