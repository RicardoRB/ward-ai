package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.Objects;
import java.util.Optional;

/**
 * One use of a name, and the binding it resolves to.
 *
 * @param name the name as the source spells it
 * @param span where the name is
 * @param access whether the name is read, written or deleted
 * @param scope the id of the scope the name appears in
 * @param resolved the binding the name refers to, or empty when no scope declares it
 * @param beforeDeclaration whether the name is used in the same body before the declaration it
 *     resolves to, such as a {@code let} read in its temporal dead zone
 */
public record NameReference(
    String name,
    Span span,
    ReferenceAccess access,
    int scope,
    Optional<Binding> resolved,
    boolean beforeDeclaration) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public NameReference {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(resolved, "resolved");
  }
}
