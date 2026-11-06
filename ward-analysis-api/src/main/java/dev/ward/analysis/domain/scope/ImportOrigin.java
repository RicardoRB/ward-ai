package dev.ward.analysis.domain.scope;

import java.util.Objects;

/**
 * Where an imported name comes from, as the import spells it.
 *
 * @param specifier the module as written, such as {@code a.b} or {@code ./util}
 * @param member the name taken from the module, or empty when the name is the module itself
 */
public record ImportOrigin(String specifier, String member) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public ImportOrigin {
    Objects.requireNonNull(specifier, "specifier");
    Objects.requireNonNull(member, "member");
  }
}
