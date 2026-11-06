package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One name a scope declares.
 *
 * @param name the name as the source spells it
 * @param kind what the name declares
 * @param declaration where the name is declared, which is the name itself
 * @param flags the properties of the binding
 * @param scope the id of the scope that declares it, an index into {@link UnitScopes#scopes()}
 * @param origin where an {@link BindingKind#IMPORT} comes from, empty for other bindings
 */
public record Binding(
    String name,
    BindingKind kind,
    Span declaration,
    Set<BindingFlag> flags,
    int scope,
    Optional<ImportOrigin> origin) {

  /**
   * Copies the flags.
   *
   * @throws NullPointerException if a field is missing
   */
  public Binding {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(origin, "origin");
    flags = Set.copyOf(flags);
  }

  /** Reports whether the binding has {@code flag}. */
  public boolean has(final BindingFlag flag) {
    return flags.contains(flag);
  }
}
