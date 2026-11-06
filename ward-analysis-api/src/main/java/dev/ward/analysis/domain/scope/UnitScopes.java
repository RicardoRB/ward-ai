package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The scopes of one source unit, the bindings each declares and every name reference with the
 * binding it resolves to. Scope 0 is always the built-in scope and scope 1 the module, so a rule
 * reads {@link #module()} to start. The model is immutable and built once per unit.
 */
public final class UnitScopes {

  /** The id of the built-in scope. */
  public static final int BUILTIN_ID = 0;

  /** The id of the module scope. */
  public static final int MODULE_ID = 1;

  static final Span NOWHERE = new Span(1, 1, 1, 1, 0, 0);

  /** The scopes of a unit that has none: an empty built-in scope around an empty module. */
  public static final UnitScopes EMPTY =
      new UnitScopes(
          List.of(
              new Scope(BUILTIN_ID, ScopeKind.BUILTIN, Scope.NO_PARENT, NOWHERE, List.of(), false),
              new Scope(MODULE_ID, ScopeKind.MODULE, BUILTIN_ID, NOWHERE, List.of(), false)),
          List.of());

  private final List<Scope> scopes;
  private final List<NameReference> references;
  private final Map<Binding, List<NameReference>> uses;

  /**
   * Creates the scopes of a unit.
   *
   * @param scopes the scopes, where the id of each is its index; the first is the built-in scope
   *     and the second the module
   * @param references every name reference, in source order
   * @throws NullPointerException if a list is missing
   */
  public UnitScopes(final List<Scope> scopes, final List<NameReference> references) {
    this.scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
    this.references = List.copyOf(Objects.requireNonNull(references, "references"));
    this.uses =
        this.references.stream()
            .filter(reference -> reference.resolved().isPresent())
            .collect(Collectors.groupingBy(reference -> reference.resolved().get()));
  }

  /** Returns every scope; the id of a scope is its index. */
  public List<Scope> scopes() {
    return scopes;
  }

  /** Returns every name reference, in source order. */
  public List<NameReference> references() {
    return references;
  }

  /** Returns the scope of the whole file. */
  public Scope module() {
    return scopes.get(MODULE_ID);
  }

  /** Returns the scope that encloses {@code scope}, or empty for the built-in scope. */
  public Optional<Scope> parent(final Scope scope) {
    return scope.parent() == Scope.NO_PARENT
        ? Optional.empty()
        : Optional.of(scopes.get(scope.parent()));
  }

  /** Returns the scopes directly inside {@code scope}, in source order. */
  public List<Scope> children(final Scope scope) {
    return scopes.stream().filter(candidate -> candidate.parent() == scope.id()).toList();
  }

  /** Returns every binding of every scope, in scope order and then source order. */
  public List<Binding> bindings() {
    return scopes.stream().flatMap(scope -> scope.bindings().stream()).toList();
  }

  /** Returns the references that resolve to no binding, in source order. */
  public List<NameReference> unresolved() {
    return references.stream().filter(reference -> reference.resolved().isEmpty()).toList();
  }

  /** Returns the references that resolve to {@code binding}, in source order. */
  public List<NameReference> usesOf(final Binding binding) {
    return uses.getOrDefault(binding, List.of());
  }

  /**
   * Reports whether names the analysis cannot see may reach {@code reference}: some scope from the
   * one it appears in out to the module is {@linkplain Scope#open() open}.
   */
  public boolean isOpen(final NameReference reference) {
    int current = reference.scope();
    while (current != Scope.NO_PARENT) {
      final Scope scope = scopes.get(current);
      if (scope.open()) {
        return true;
      }
      current = scope.parent();
    }
    return false;
  }

  /**
   * Returns these scopes with {@code names} declared in the built-in scope, and every unresolved
   * reference to one of them resolved to it.
   */
  public UnitScopes withBuiltins(final Set<String> names) {
    return ScopeBuiltins.apply(this, names);
  }
}
