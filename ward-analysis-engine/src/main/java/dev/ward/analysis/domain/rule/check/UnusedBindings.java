package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/** The local names that nothing reads, which the unused-variable checks judge. */
public final class UnusedBindings {

  private static final String IGNORED_PREFIX = "_";
  private static final Set<ScopeKind> LOCAL_SCOPES =
      Set.of(ScopeKind.FUNCTION, ScopeKind.BLOCK, ScopeKind.LAMBDA, ScopeKind.COMPREHENSION);
  private static final Set<BindingKind> LOCAL_KINDS =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT);
  private static final Set<BindingFlag> EXEMPT_FLAGS =
      Set.of(
          BindingFlag.GLOBAL, BindingFlag.NONLOCAL, BindingFlag.DESTRUCTURED, BindingFlag.EXPORTED);

  private UnusedBindings() {}

  /** True when the scope is a function, block, lambda or comprehension scope. */
  public static boolean isLocalScope(final Scope scope) {
    return LOCAL_SCOPES.contains(scope.kind());
  }

  /**
   * True when the binding is a local variable or constant that no use reads. Names that start with
   * {@code _}, unpacked or loop-header names, and names a nested scope declares global or nonlocal
   * are not judged.
   */
  public static boolean isUnused(final Binding binding, final UnitScopes scopes) {
    return LOCAL_KINDS.contains(binding.kind())
        && !binding.name().startsWith(IGNORED_PREFIX)
        && EXEMPT_FLAGS.stream().noneMatch(binding::has)
        && scopes.usesOf(binding).stream().noneMatch(use -> use.access() != ReferenceAccess.WRITE);
  }
}
