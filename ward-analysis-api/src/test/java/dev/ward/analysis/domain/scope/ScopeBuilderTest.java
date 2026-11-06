package dev.ward.analysis.domain.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeBuilderTest {

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
  }

  private static ScopeBuilder builder(final ClassScopes classScopes) {
    return new ScopeBuilder(new Span(1, 1, 9, 1, 0, 100), classScopes);
  }

  @Test
  void aNewBuilderHasABuiltinScopeAndAModule() {
    final UnitScopes scopes = builder(ClassScopes.VISIBLE).build();

    assertThat(scopes.scopes())
        .extracting(Scope::kind)
        .containsExactly(ScopeKind.BUILTIN, ScopeKind.MODULE);
    assertThat(scopes.module().parent()).isEqualTo(UnitScopes.BUILTIN_ID);
    assertThat(scopes.parent(scopes.builtin())).isEmpty();
  }

  @Test
  void aReferenceResolvesToADeclarationThatComesLater() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.open(ScopeKind.FUNCTION, at(0));
    builder.reference("later", at(10), ReferenceAccess.READ);
    builder.close();
    builder.declare("later", BindingKind.VARIABLE, at(20), Set.of());

    final NameReference reference = builder.build().references().get(0);

    assertThat(reference.resolved()).isPresent();
    assertThat(reference.beforeDeclaration()).isFalse();
  }

  @Test
  void aReferenceInTheSameBodyBeforeItsDeclarationIsFlagged() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("x", at(5), ReferenceAccess.READ);
    builder.declare("x", BindingKind.VARIABLE, at(10), Set.of(BindingFlag.LEXICAL));

    assertThat(builder.build().references().get(0).beforeDeclaration()).isTrue();
  }

  @Test
  void aHoistedFunctionIsNeverBeforeItsDeclaration() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("f", at(5), ReferenceAccess.READ);
    builder.declare("f", BindingKind.FUNCTION, at(10), Set.of(BindingFlag.HOISTED));

    assertThat(builder.build().references().get(0).beforeDeclaration()).isFalse();
  }

  @Test
  void aRedeclarationIsKeptAndALaterUseSeesTheLatestDeclaration() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.declare("x", BindingKind.VARIABLE, at(1), Set.of());
    builder.declare("x", BindingKind.VARIABLE, at(10), Set.of());
    builder.reference("x", at(20), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.module().bindings()).hasSize(2);
    assertThat(scopes.references().get(0).resolved().orElseThrow().declaration().startOffset())
        .isEqualTo(10);
  }

  @Test
  void skippedClassScopesAreInvisibleToNestedFunctionsButNotToTheBodyItself() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.open(ScopeKind.CLASS, at(0));
    builder.declare("member", BindingKind.VARIABLE, at(1), Set.of());
    builder.reference("member", at(2), ReferenceAccess.READ);
    builder.open(ScopeKind.FUNCTION, at(3));
    builder.reference("member", at(4), ReferenceAccess.READ);

    final List<NameReference> references = builder.build().references();

    assertThat(references.get(0).resolved()).isPresent();
    assertThat(references.get(1).resolved()).isEmpty();
  }

  @Test
  void visibleClassScopesAreSeenByNestedFunctions() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, at(0));
    builder.declare("member", BindingKind.VARIABLE, at(1), Set.of());
    builder.open(ScopeKind.FUNCTION, at(3));
    builder.reference("member", at(4), ReferenceAccess.READ);

    assertThat(builder.build().references().get(0).resolved()).isPresent();
  }

  @Test
  void globalAndNonlocalAliasesSendTheLookupOutward() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.declare("g", BindingKind.VARIABLE, at(1), Set.of());
    builder.open(ScopeKind.FUNCTION, at(2));
    builder.declare("n", BindingKind.VARIABLE, at(3), Set.of());
    builder.open(ScopeKind.FUNCTION, at(4));
    builder.declare("g", BindingKind.VARIABLE, at(5), Set.of(BindingFlag.GLOBAL));
    builder.declare("n", BindingKind.VARIABLE, at(6), Set.of(BindingFlag.NONLOCAL));
    builder.reference("g", at(7), ReferenceAccess.WRITE);
    builder.reference("n", at(8), ReferenceAccess.WRITE);

    final List<NameReference> references = builder.build().references();

    assertThat(references.get(0).resolved().orElseThrow().scope()).isEqualTo(UnitScopes.MODULE_ID);
    assertThat(references.get(1).resolved().orElseThrow().declaration().startOffset()).isEqualTo(3);
  }

  @Test
  void aNonlocalWithNoEnclosingBindingIsUnresolved() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.open(ScopeKind.FUNCTION, at(2));
    builder.declare("n", BindingKind.VARIABLE, at(3), Set.of(BindingFlag.NONLOCAL));
    builder.reference("n", at(4), ReferenceAccess.WRITE);

    assertThat(builder.build().unresolved()).hasSize(1);
  }

  @Test
  void openScopesMakeEveryReferenceBelowThemOpen() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final int function = builder.open(ScopeKind.FUNCTION, at(1));
    builder.markOpen(function);
    builder.reference("a", at(2), ReferenceAccess.READ);
    builder.close();
    builder.reference("b", at(5), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.isOpen(scopes.references().get(0))).isTrue();
    assertThat(scopes.isOpen(scopes.references().get(1))).isFalse();
  }

  @Test
  void referencesComeBackInSourceOrderWhateverTheOrderTheyWereRecorded() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("b", at(9), ReferenceAccess.READ);
    builder.reference("a", at(3), ReferenceAccess.READ);

    assertThat(builder.build().references())
        .extracting(NameReference::name)
        .containsExactly("a", "b");
  }

  @Test
  void usesOfListsTheReferencesToABinding() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final Binding x = builder.declare("x", BindingKind.VARIABLE, at(1), Set.of());
    builder.reference("x", at(5), ReferenceAccess.READ);
    builder.reference("y", at(6), ReferenceAccess.READ);
    builder.reference("x", at(7), ReferenceAccess.WRITE);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.usesOf(x))
        .extracting(NameReference::access)
        .containsExactly(ReferenceAccess.READ, ReferenceAccess.WRITE);
    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("y");
  }

  @Test
  void childrenAndParentFollowTheNesting() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final int function = builder.open(ScopeKind.FUNCTION, at(1));
    builder.open(ScopeKind.BLOCK, at(2));

    final UnitScopes scopes = builder.build();

    assertThat(scopes.children(scopes.module())).extracting(Scope::id).containsExactly(function);
    assertThat(scopes.parent(scopes.scopes().get(function + 1)))
        .contains(scopes.scopes().get(function));
    assertThat(builder.nearest(Set.of(ScopeKind.FUNCTION))).isEqualTo(function);
    assertThat(builder.kindOf(function)).isEqualTo(ScopeKind.FUNCTION);
    assertThat(builder.parentOf(function)).isEqualTo(UnitScopes.MODULE_ID);
  }

  @Test
  void builtinsResolveTheNamesNothingElseDeclares() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.declare("len", BindingKind.VARIABLE, at(1), Set.of());
    builder.reference("len", at(5), ReferenceAccess.READ);
    builder.reference("print", at(6), ReferenceAccess.READ);
    builder.reference("nothing", at(7), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build().withBuiltins(Set.of("len", "print"));

    assertThat(scopes.builtin().bindings())
        .extracting(Binding::name)
        .containsExactly("len", "print");
    assertThat(scopes.references().get(0).resolved().orElseThrow().kind())
        .isEqualTo(BindingKind.VARIABLE);
    assertThat(scopes.references().get(1).resolved().orElseThrow().kind())
        .isEqualTo(BindingKind.BUILTIN);
    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("nothing");
  }

  @Test
  void theDumpListsScopesBindingsAndReferences() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.open(ScopeKind.FUNCTION, at(0));
    builder.declare("a", BindingKind.PARAMETER, at(1), Set.of());
    builder.reference("a", at(2), ReferenceAccess.READ);
    builder.reference("b", at(3), ReferenceAccess.READ);
    builder.declareImport(
        "m", at(4), new ImportOrigin("pkg", "m", at(4)), Set.of(BindingFlag.TYPE_ONLY));

    final String dump = builder.build().dump();

    assertThat(dump)
        .contains(
            "module\n  function @1:1\n    parameter a @1:2\n    import m @1:5 [type_only] from pkg")
        .contains("1:3 a read -> function parameter a @1:2")
        .contains("1:4 b read -> unresolved");
  }

  @Test
  void anEmptyUnitHasEmptyScopes() {
    assertThat(UnitScopes.EMPTY.references()).isEmpty();
    assertThat(UnitScopes.EMPTY.bindings()).isEmpty();
    assertThat(NoScopes.INSTANCE.builtins(Set.of())).isEmpty();
  }
}
