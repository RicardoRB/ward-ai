package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One access of a local variable or parameter, made by a node of a basic block.
 *
 * <p>The declaration identifies the variable: two accesses with the same name and declaration span
 * touch the same variable, so block-scoped names that are reused in sibling blocks stay apart.
 *
 * @param name the name of the variable
 * @param kind what the access does
 * @param span where the access is in the source
 * @param declaration where the variable is declared
 */
public record VariableAccess(String name, AccessKind kind, Span span, Span declaration) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public VariableAccess {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(declaration, "declaration");
  }
}
