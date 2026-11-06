package dev.ward.analysis.domain.scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Declares the built-in names of a language in the built-in scope of a unit. */
final class ScopeBuiltins {

  private ScopeBuiltins() {}

  /** Returns {@code unit} with {@code names} declared and the unresolved uses of them resolved. */
  static UnitScopes apply(final UnitScopes unit, final Set<String> names) {
    final List<Binding> builtins =
        names.stream()
            .sorted()
            .map(
                name ->
                    new Binding(
                        name,
                        BindingKind.BUILTIN,
                        UnitScopes.NOWHERE,
                        Set.of(),
                        UnitScopes.BUILTIN_ID,
                        Optional.empty()))
            .toList();
    final Map<String, Binding> byName = new HashMap<>();
    builtins.forEach(binding -> byName.put(binding.name(), binding));
    final List<Scope> replaced = new ArrayList<>(unit.scopes());
    final Scope root = unit.scopes().get(UnitScopes.BUILTIN_ID);
    replaced.set(
        UnitScopes.BUILTIN_ID,
        new Scope(root.id(), root.kind(), root.parent(), root.span(), builtins, root.open()));
    return new UnitScopes(
        replaced, unit.references().stream().map(use -> bind(use, byName)).toList());
  }

  private static NameReference bind(final NameReference reference, final Map<String, Binding> by) {
    final Binding builtin = by.get(reference.name());
    if (reference.resolved().isPresent() || builtin == null) {
      return reference;
    }
    return new NameReference(
        reference.name(),
        reference.span(),
        reference.access(),
        reference.scope(),
        Optional.of(builtin),
        false);
  }
}
