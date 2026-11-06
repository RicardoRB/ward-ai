package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the scopes, bindings and references a language walker finds, then resolves every
 * reference when {@link #build()} is called, so a name may be used before the walker sees its
 * declaration, as hoisting and closures allow. The walker opens a scope, declares into it, and
 * closes it again; the built-in scope and the module scope exist from the start.
 */
public final class ScopeBuilder {

  private record Pending(String name, Span span, ReferenceAccess access, int scope) {}

  private final ClassScopes classScopes;
  private final List<Span> spans = new ArrayList<>();
  private final List<ScopeKind> kinds = new ArrayList<>();
  private final List<Integer> parents = new ArrayList<>();
  private final List<List<Binding>> bindings = new ArrayList<>();
  private final List<Boolean> open = new ArrayList<>();
  private final List<Pending> pending = new ArrayList<>();
  private final Deque<Integer> stack = new ArrayDeque<>();

  /**
   * Starts the scopes of a unit that spans {@code unit}.
   *
   * @param unit the region of the whole file
   * @param classScopes whether code in a class body's functions sees the class's names
   */
  public ScopeBuilder(final Span unit, final ClassScopes classScopes) {
    this.classScopes = classScopes;
    add(ScopeKind.BUILTIN, Scope.NO_PARENT, unit);
    add(ScopeKind.MODULE, UnitScopes.BUILTIN_ID, unit);
    stack.push(UnitScopes.MODULE_ID);
  }

  private int add(final ScopeKind kind, final int parent, final Span span) {
    kinds.add(kind);
    parents.add(parent);
    spans.add(span);
    bindings.add(new ArrayList<>());
    open.add(Boolean.FALSE);
    return kinds.size() - 1;
  }

  /** Opens a scope inside the current one and makes it current; returns its id. */
  public int open(final ScopeKind kind, final Span span) {
    final int id = add(kind, stack.peek(), span);
    stack.push(id);
    return id;
  }

  /** Makes the enclosing scope current again. */
  public void close() {
    stack.pop();
  }

  /** Returns the id of the current scope. */
  public int current() {
    return stack.peek();
  }

  /** Returns the kind of scope {@code id}. */
  public ScopeKind kindOf(final int id) {
    return kinds.get(id);
  }

  /** Returns the id of the scope that encloses scope {@code id}. */
  public int parentOf(final int id) {
    return parents.get(id);
  }

  /**
   * Returns the id of the nearest scope from the current one outwards that has one of {@code of}.
   */
  public int nearest(final Set<ScopeKind> of) {
    int id = current();
    while (!of.contains(kinds.get(id)) && parents.get(id) != Scope.NO_PARENT) {
      id = parents.get(id);
    }
    return id;
  }

  /** Marks scope {@code id} open: names the analysis cannot see may be added to it. */
  public void markOpen(final int id) {
    open.set(id, Boolean.TRUE);
  }

  /** Reports whether scope {@code id} already declares {@code name}. */
  public boolean declares(final int id, final String name) {
    return bindings.get(id).stream().anyMatch(binding -> binding.name().equals(name));
  }

  /** Returns the latest binding of {@code name} that scope {@code id} declares. */
  public Optional<Binding> declared(final int id, final String name) {
    final List<Binding> here = bindings.get(id);
    for (int index = here.size() - 1; index >= 0; index--) {
      if (here.get(index).name().equals(name)) {
        return Optional.of(here.get(index));
      }
    }
    return Optional.empty();
  }

  /** Declares {@code name} in the current scope; returns the binding. */
  public Binding declare(
      final String name, final BindingKind kind, final Span span, final Set<BindingFlag> flags) {
    return declareIn(current(), name, kind, span, flags);
  }

  /** Declares {@code name} in scope {@code id}; returns the binding. */
  public Binding declareIn(
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
  public Binding declareImport(
      final String name, final Span span, final ImportOrigin origin, final Set<BindingFlag> flags) {
    final Binding binding =
        new Binding(name, BindingKind.IMPORT, span, flags, current(), Optional.of(origin));
    bindings.get(current()).add(binding);
    return binding;
  }

  /** Records a use of {@code name} in the current scope. */
  public void reference(final String name, final Span span, final ReferenceAccess access) {
    referenceIn(current(), name, span, access);
  }

  /** Records a use of {@code name} in scope {@code id}. */
  public void referenceIn(
      final int id, final String name, final Span span, final ReferenceAccess access) {
    pending.add(new Pending(name, span, access, id));
  }

  /** Resolves every reference and returns the scopes of the unit. */
  public UnitScopes build() {
    final List<Scope> scopes = new ArrayList<>();
    for (int id = 0; id < kinds.size(); id++) {
      scopes.add(
          new Scope(
              id, kinds.get(id), parents.get(id), spans.get(id), bindings.get(id), open.get(id)));
    }
    final ScopeResolver resolver = new ScopeResolver(scopes, classScopes);
    final List<NameReference> references =
        pending.stream()
            .sorted(java.util.Comparator.comparingInt(p -> p.span().startOffset()))
            .map(p -> resolved(resolver, p))
            .toList();
    return new UnitScopes(scopes, references);
  }

  private static NameReference resolved(final ScopeResolver resolver, final Pending use) {
    final Optional<ScopeResolver.Found> found =
        resolver.resolve(use.name(), use.scope(), use.span().startOffset());
    return new NameReference(
        use.name(),
        use.span(),
        use.access(),
        use.scope(),
        found.map(ScopeResolver.Found::binding),
        found.map(ScopeResolver.Found::before).orElse(false));
  }
}
