package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Objects;

/**
 * One lexical scope. Scopes form a tree through {@code parent}; the ids index {@link
 * UnitScopes#scopes()}, so the model holds no cycles.
 *
 * @param id the index of the scope in its unit
 * @param kind what opens the scope
 * @param parent the id of the enclosing scope, or {@link #NO_PARENT} for the built-in scope
 * @param span the region the scope covers
 * @param bindings the names the scope declares, in source order; a name may appear twice when it is
 *     declared twice
 * @param open whether names the analysis cannot see may be added to the scope, such as by a star
 *     import or {@code exec}; a name that resolves nowhere in an open scope is not undefined
 */
public record Scope(
    int id, ScopeKind kind, int parent, Span span, List<Binding> bindings, boolean open) {

  /** The parent of the outermost scope. */
  public static final int NO_PARENT = -1;

  /**
   * Copies the bindings.
   *
   * @throws NullPointerException if a field is missing
   */
  public Scope {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(span, "span");
    bindings = List.copyOf(bindings);
  }
}
