package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The scopes a walker has opened and the names they declare. {@link ScopeBuilder} adds the cursor
 * and the references to it.
 */
public abstract class ScopeDeclarations {

  final List<Span> spans = new ArrayList<>();
  final List<ScopeKind> kinds = new ArrayList<>();
  final List<Integer> parents = new ArrayList<>();
  final List<Boolean> open = new ArrayList<>();
  private final List<List<Binding>> bindings = new ArrayList<>();

  /** Returns the id of the current scope. */
  public abstract int current();

  final int add(final ScopeKind kind, final int parent, final Span span) {
    kinds.add(kind);
    parents.add(parent);
    spans.add(span);
    bindings.add(new ArrayList<>());
    open.add(Boolean.FALSE);
    return kinds.size() - 1;
  }

  final List<Binding> bindingsOf(final int id) {
    return bindings.get(id);
  }

  /** Returns the latest binding of {@code name} that scope {@code id} declares. */
  public final Optional<Binding> declared(final int id, final String name) {
    final List<Binding> here = bindings.get(id);
    for (int index = here.size() - 1; index >= 0; index--) {
      if (here.get(index).name().equals(name)) {
        return Optional.of(here.get(index));
      }
    }
    return Optional.empty();
  }

  /** Reports whether scope {@code id} already declares {@code name}. */
  public final boolean declares(final int id, final String name) {
    return declared(id, name).isPresent();
  }

  /** Declares {@code name} in the current scope; returns the binding. */
  public final Binding declare(
      final String name, final BindingKind kind, final Span span, final Set<BindingFlag> flags) {
    return declareIn(current(), name, kind, span, flags);
  }

  /** Declares {@code name} in scope {@code id}; returns the binding. */
  public final Binding declareIn(
      final int id,
      final String name,
      final BindingKind kind,
      final Span span,
      final Set<BindingFlag> flags) {
    final Binding binding = new Binding(name, kind, span, flags, id, Optional.empty());
    bindings.get(id).add(binding);
    return binding;
  }

  /** Declares an imported name in the current scope; returns the binding. */
  public final Binding declareImport(
      final String name, final Span span, final ImportOrigin origin, final Set<BindingFlag> flags) {
    final Binding binding =
        new Binding(name, BindingKind.IMPORT, span, flags, current(), Optional.of(origin));
    bindings.get(current()).add(binding);
    return binding;
  }
}
