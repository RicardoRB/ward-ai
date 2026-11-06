package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.Objects;

/**
 * Where an imported name comes from, as the import spells it.
 *
 * @param specifier the module as written, such as {@code a.b} or {@code ./util}
 * @param member the name taken from the module, or empty when the name is the module itself
 * @param site the region of the import that names the module, which is where the dependency graph
 *     records the reference that this import resolves through
 */
public record ImportOrigin(String specifier, String member, Span site) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public ImportOrigin {
    Objects.requireNonNull(specifier, "specifier");
    Objects.requireNonNull(member, "member");
    Objects.requireNonNull(site, "site");
  }
}
