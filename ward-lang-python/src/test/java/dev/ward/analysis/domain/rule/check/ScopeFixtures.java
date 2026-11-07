package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Hand-made lexical scopes, bindings and references for the scope-based check tests. */
final class ScopeFixtures {

  /** The span of every scope and of the references, which the tests do not need to tell apart. */
  static final Span WHOLE = Nodes.at(0, 100);

  static final Span READ = Nodes.at(40, 1);

  private static final int BUILTIN = 0;
  private static final int MODULE = 1;
  private static final int FUNCTION = 2;

  private ScopeFixtures() {}

  /** A builtin, a module and a function scope; the function holds the given bindings. */
  static UnitScopes functionScopes(final Binding local, final NameReference read) {
    return new UnitScopes(
        List.of(
            new Scope(BUILTIN, ScopeKind.BUILTIN, Scope.NO_PARENT, WHOLE, List.of(), false),
            new Scope(MODULE, ScopeKind.MODULE, BUILTIN, WHOLE, List.of(), false),
            new Scope(FUNCTION, ScopeKind.FUNCTION, MODULE, WHOLE, List.of(local), false)),
        List.of(read));
  }

  /** A module that holds the loop variable, read from a function nested in the loop. */
  static UnitScopes loopScopes(final Binding loopVar, final NameReference read) {
    return new UnitScopes(
        List.of(
            new Scope(BUILTIN, ScopeKind.BUILTIN, Scope.NO_PARENT, WHOLE, List.of(), false),
            new Scope(MODULE, ScopeKind.MODULE, BUILTIN, WHOLE, List.of(loopVar), false),
            new Scope(FUNCTION, ScopeKind.FUNCTION, MODULE, WHOLE, List.of(), false)),
        List.of(read));
  }

  /** A module with the given bindings, open when a star import leaves it open. */
  static UnitScopes moduleScopes(final boolean open, final Binding... bindings) {
    return new UnitScopes(
        List.of(
            new Scope(BUILTIN, ScopeKind.BUILTIN, Scope.NO_PARENT, WHOLE, List.of(), false),
            new Scope(MODULE, ScopeKind.MODULE, BUILTIN, WHOLE, List.of(bindings), open)),
        List.of());
  }

  static Binding binding(
      final String name, final BindingKind kind, final Span span, final int scope) {
    return new Binding(name, kind, span, Set.of(), scope, Optional.empty());
  }

  static NameReference reference(
      final String name, final boolean before, final int scope, final ReferenceAccess access) {
    return new NameReference(name, READ, access, scope, Optional.empty(), before);
  }

  static NameReference resolvedReference(
      final String name, final int scope, final Binding binding) {
    return new NameReference(name, READ, ReferenceAccess.READ, scope, Optional.of(binding), false);
  }
}
