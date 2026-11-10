package dev.ward.analysis.domain.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeModelTest {

  private static final int OFFSET_0 = 0;
  private static final int OFFSET_1 = 1;
  private static final int OFFSET_2 = 2;
  private static final int OFFSET_3 = 3;
  private static final int OFFSET_4 = 4;
  private static final int OFFSET_5 = 5;
  private static final int OFFSET_6 = 6;
  private static final int OFFSET_7 = 7;
  private static final int OFFSET_9 = 9;
  private static final int LAST_LINE = 9;
  private static final int END_OFFSET = 100;

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
  }

  private static ScopeBuilder builder(final ClassScopes classScopes) {
    return new ScopeBuilder(new Span(1, 1, LAST_LINE, 1, 0, END_OFFSET), classScopes);
  }

  @Test
  void openScopesMakeEveryReferenceBelowThemOpen() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final int function = builder.open(ScopeKind.FUNCTION, at(OFFSET_1));
    builder.markOpen(function);
    builder.reference("a", at(OFFSET_2), ReferenceAccess.READ);
    builder.close();
    builder.reference("b", at(OFFSET_5), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.isOpen(scopes.references().get(0))).isTrue();
    assertThat(scopes.isOpen(scopes.references().get(1))).isFalse();
  }

  @Test
  void referencesComeBackInSourceOrderWhateverTheOrderTheyWereRecorded() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("b", at(OFFSET_9), ReferenceAccess.READ);
    builder.reference("a", at(OFFSET_3), ReferenceAccess.READ);

    assertThat(builder.build().references())
        .extracting(NameReference::name)
        .containsExactly("a", "b");
  }

  @Test
  void usesOfListsTheReferencesToABinding() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final Binding x = builder.declare("x", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.reference("x", at(OFFSET_5), ReferenceAccess.READ);
    builder.reference("y", at(OFFSET_6), ReferenceAccess.READ);
    builder.reference("x", at(OFFSET_7), ReferenceAccess.WRITE);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.usesOf(x))
        .extracting(NameReference::access)
        .containsExactly(ReferenceAccess.READ, ReferenceAccess.WRITE);
    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("y");
  }

  @Test
  void childrenAndParentFollowTheNesting() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    final int function = builder.open(ScopeKind.FUNCTION, at(OFFSET_1));
    builder.open(ScopeKind.BLOCK, at(OFFSET_2));

    final UnitScopes scopes = builder.build();

    assertThat(scopes.children(scopes.module())).extracting(Scope::id).containsExactly(function);
    assertThat(scopes.parent(scopes.scopes().get(function + 1)))
        .contains(scopes.scopes().get(function));
    assertThat(builder.nearest(Set.of(ScopeKind.FUNCTION))).isEqualTo(function);
    assertThat(builder.kindOf(function)).isEqualTo(ScopeKind.FUNCTION);
    assertThat(builder.build().scopes().get(function).parent()).isEqualTo(UnitScopes.MODULE_ID);
  }

  @Test
  void builtinsResolveTheNamesNothingElseDeclares() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.declare("len", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.reference("len", at(OFFSET_5), ReferenceAccess.READ);
    builder.reference("print", at(OFFSET_6), ReferenceAccess.READ);
    builder.reference("nothing", at(OFFSET_7), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build().withBuiltins(Set.of("len", "print"));

    assertThat(scopes.scopes().get(UnitScopes.BUILTIN_ID).bindings())
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
    builder.open(ScopeKind.FUNCTION, at(OFFSET_0));
    builder.declare("a", BindingKind.PARAMETER, at(OFFSET_1), Set.of());
    builder.reference("a", at(OFFSET_2), ReferenceAccess.READ);
    builder.reference("b", at(OFFSET_3), ReferenceAccess.READ);
    builder.declareImport(
        "m",
        at(OFFSET_4),
        new ImportOrigin("pkg", "m", at(OFFSET_4)),
        Set.of(BindingFlag.TYPE_ONLY));

    final String dump = ScopeDump.of(builder.build());

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

  @Test
  void aNonlocalWithNoEnclosingBindingIsUnresolved() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.open(ScopeKind.FUNCTION, at(OFFSET_2));
    builder.declare("n", BindingKind.VARIABLE, at(OFFSET_3), Set.of(BindingFlag.NONLOCAL));
    builder.reference("n", at(OFFSET_4), ReferenceAccess.WRITE);

    assertThat(builder.build().unresolved()).hasSize(1);
  }
}
