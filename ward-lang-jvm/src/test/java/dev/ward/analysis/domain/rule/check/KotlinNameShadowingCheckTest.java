package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin name-shadowing check over hand-made scope models of a unit. */
class KotlinNameShadowingCheckTest {

  private static final String MESSAGE = "name shadowed: {value}";
  private static final int MODULE = 1;
  private static final int FUNCTION = 2;
  private static final int BLOCK = 3;
  private static final int SIBLING = 4;
  private static final int EARLY = 10;
  private static final int LATE = 30;

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
  }

  private static Binding binding(
      final String name, final BindingKind kind, final int offset, final int scope) {
    return new Binding(name, kind, at(offset), Set.of(), scope, Optional.empty());
  }

  private static Scope scope(
      final int id, final ScopeKind kind, final int parent, final List<Binding> bindings) {
    return new Scope(id, kind, parent, at(0), bindings, false);
  }

  private static List<dev.ward.analysis.domain.rule.RuleMatch> matches(final List<Scope> scopes) {
    final ScopeView view =
        new ScopeView(new UnitScopes(scopes, List.of()), Map.of(), Set.of(), NoScopes.INSTANCE);
    final SyntaxNode root = TokenTrees.tok("token", "x", 1, 0);
    return new KotlinNameShadowingCheck(MESSAGE).matches(root, null, view, (SourceUnit) null);
  }

  private static List<Scope> outerWithInnerBlock(final int outerOffset, final int innerOffset) {
    return List.of(
        scope(0, ScopeKind.BUILTIN, Scope.NO_PARENT, List.of()),
        scope(MODULE, ScopeKind.MODULE, 0, List.of()),
        scope(
            FUNCTION,
            ScopeKind.FUNCTION,
            MODULE,
            List.of(binding("total", BindingKind.PARAMETER, outerOffset, FUNCTION))),
        scope(
            BLOCK,
            ScopeKind.BLOCK,
            FUNCTION,
            List.of(binding("total", BindingKind.VARIABLE, innerOffset, BLOCK))));
  }

  @Test
  void reportsALocalThatShadowsAnEarlierOuterName() {
    assertThat(matches(outerWithInnerBlock(EARLY, LATE))).hasSize(1);
  }

  @Test
  void acceptsAnInnerNameThatIsDeclaredBeforeTheOuterOne() {
    assertThat(matches(outerWithInnerBlock(LATE, EARLY))).isEmpty();
  }

  @Test
  void acceptsSiblingScopesThatReuseAName() {
    final List<Scope> scopes =
        List.of(
            scope(0, ScopeKind.BUILTIN, Scope.NO_PARENT, List.of()),
            scope(MODULE, ScopeKind.MODULE, 0, List.of()),
            scope(FUNCTION, ScopeKind.FUNCTION, MODULE, List.of()),
            scope(
                BLOCK,
                ScopeKind.BLOCK,
                FUNCTION,
                List.of(binding("value", BindingKind.VARIABLE, 10, BLOCK))),
            scope(
                SIBLING,
                ScopeKind.BLOCK,
                FUNCTION,
                List.of(binding("value", BindingKind.VARIABLE, 40, SIBLING))));

    assertThat(matches(scopes)).isEmpty();
  }

  @Test
  void stopsAtAClassSoMembersAreNotCompared() {
    final List<Scope> scopes =
        List.of(
            scope(0, ScopeKind.BUILTIN, Scope.NO_PARENT, List.of()),
            scope(MODULE, ScopeKind.MODULE, 0, List.of()),
            scope(
                FUNCTION,
                ScopeKind.CLASS,
                MODULE,
                List.of(binding("total", BindingKind.VARIABLE, 10, FUNCTION))),
            scope(
                BLOCK,
                ScopeKind.BLOCK,
                FUNCTION,
                List.of(binding("total", BindingKind.VARIABLE, 30, BLOCK))));

    assertThat(matches(scopes)).isEmpty();
  }
}
