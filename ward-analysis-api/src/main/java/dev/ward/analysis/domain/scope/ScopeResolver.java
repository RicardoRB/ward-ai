package dev.ward.analysis.domain.scope;

import java.util.List;
import java.util.Optional;

/**
 * Finds the binding a name refers to by walking from its scope outwards. A function-level {@code
 * global} or {@code nonlocal} alias sends the search to the module or to the enclosing functions.
 */
final class ScopeResolver {

  private final List<Scope> scopes;
  private final ClassScopes classScopes;

  ScopeResolver(final List<Scope> scopes, final ClassScopes classScopes) {
    this.scopes = scopes;
    this.classScopes = classScopes;
  }

  /** Resolves {@code name} used at {@code offset} in scope {@code from}. */
  Optional<Found> resolve(final String name, final int from, final int offset) {
    return search(name, from, from, offset);
  }

  private Optional<Found> search(
      final String name, final int start, final int first, final int offset) {
    int current = first;
    while (current != Scope.NO_PARENT) {
      final Scope scope = scopes.get(current);
      final List<Binding> candidates = candidates(scope, name, current == start);
      if (!candidates.isEmpty()) {
        final Found found = choose(candidates, start, offset);
        if (!isAlias(found.binding(), scope)) {
          return Optional.of(found);
        }
        return found.binding().has(BindingFlag.GLOBAL)
            ? search(name, start, moduleOf(current), offset)
            : search(name, start, scope.parent(), offset);
      }
      current = scope.parent();
    }
    return Optional.empty();
  }

  private static boolean isAlias(final Binding binding, final Scope scope) {
    return scope.kind() != ScopeKind.MODULE
        && (binding.has(BindingFlag.GLOBAL) || binding.has(BindingFlag.NONLOCAL));
  }

  private int moduleOf(final int id) {
    int current = id;
    while (scopes.get(current).kind() != ScopeKind.MODULE) {
      current = scopes.get(current).parent();
    }
    return current;
  }

  private List<Binding> candidates(final Scope scope, final String name, final boolean own) {
    if (scope.kind() == ScopeKind.CLASS && classScopes == ClassScopes.SKIPPED && !own) {
      return List.of();
    }
    return scope.bindings().stream().filter(binding -> binding.name().equals(name)).toList();
  }

  private Found choose(final List<Binding> candidates, final int start, final int offset) {
    final Binding last = latestBefore(candidates, offset);
    if (last == null) {
      final Binding first = candidates.get(0);
      return new Found(first, isBefore(first, start));
    }
    return new Found(last, false);
  }

  private static Binding latestBefore(final List<Binding> candidates, final int offset) {
    Binding chosen = null;
    for (final Binding candidate : candidates) {
      if (candidate.declaration().startOffset() <= offset) {
        chosen = candidate;
      }
    }
    return chosen;
  }

  /** Whether a use in scope {@code start} runs in the same body as the declaration. */
  private boolean isBefore(final Binding binding, final int start) {
    final boolean hoistedFunction =
        binding.kind() == BindingKind.FUNCTION && binding.has(BindingFlag.HOISTED);
    final boolean comprehension = scopes.get(binding.scope()).kind() == ScopeKind.COMPREHENSION;
    return !hoistedFunction && !comprehension && body(start) == body(binding.scope());
  }

  /** The id of the nearest function, lambda or module around scope {@code id}. */
  private int body(final int id) {
    int current = id;
    while (current != Scope.NO_PARENT) {
      final ScopeKind kind = scopes.get(current).kind();
      if (kind == ScopeKind.FUNCTION || kind == ScopeKind.LAMBDA || kind == ScopeKind.MODULE) {
        return current;
      }
      current = scopes.get(current).parent();
    }
    return Scope.NO_PARENT;
  }

  /** What a lookup found: the binding, and whether it is declared after the use. */
  record Found(Binding binding, boolean before) {}
}
