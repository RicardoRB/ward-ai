package dev.ward.analysis.domain.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeBuilderTest {

  private static final int OFFSET_0 = 0;
  private static final int OFFSET_1 = 1;
  private static final int OFFSET_2 = 2;
  private static final int OFFSET_3 = 3;
  private static final int OFFSET_4 = 4;
  private static final int OFFSET_5 = 5;
  private static final int OFFSET_6 = 6;
  private static final int OFFSET_7 = 7;
  private static final int OFFSET_8 = 8;
  private static final int OFFSET_10 = 10;
  private static final int OFFSET_20 = 20;
  private static final int LAST_LINE = 9;
  private static final int END_OFFSET = 100;

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
  }

  private static ScopeBuilder builder(final ClassScopes classScopes) {
    return new ScopeBuilder(new Span(1, 1, LAST_LINE, 1, 0, END_OFFSET), classScopes);
  }

  @Test
  void aNewBuilderHasABuiltinScopeAndAModule() {
    final UnitScopes scopes = builder(ClassScopes.VISIBLE).build();

    assertThat(scopes.scopes())
        .extracting(Scope::kind)
        .containsExactly(ScopeKind.BUILTIN, ScopeKind.MODULE);
    assertThat(scopes.module().parent()).isEqualTo(UnitScopes.BUILTIN_ID);
    assertThat(scopes.parent(scopes.scopes().get(UnitScopes.BUILTIN_ID))).isEmpty();
  }

  @Test
  void aReferenceResolvesToADeclarationThatComesLater() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.open(ScopeKind.FUNCTION, at(OFFSET_0));
    builder.reference("later", at(OFFSET_10), ReferenceAccess.READ);
    builder.close();
    builder.declare("later", BindingKind.VARIABLE, at(OFFSET_20), Set.of());

    final NameReference reference = builder.build().references().get(0);

    assertThat(reference.resolved()).isPresent();
    assertThat(reference.beforeDeclaration()).isFalse();
  }

  @Test
  void aReferenceInTheSameBodyBeforeItsDeclarationIsFlagged() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("x", at(OFFSET_5), ReferenceAccess.READ);
    builder.declare("x", BindingKind.VARIABLE, at(OFFSET_10), Set.of(BindingFlag.LEXICAL));

    assertThat(builder.build().references().get(0).beforeDeclaration()).isTrue();
  }

  @Test
  void aHoistedFunctionIsNeverBeforeItsDeclaration() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.reference("f", at(OFFSET_5), ReferenceAccess.READ);
    builder.declare("f", BindingKind.FUNCTION, at(OFFSET_10), Set.of(BindingFlag.HOISTED));

    assertThat(builder.build().references().get(0).beforeDeclaration()).isFalse();
  }

  @Test
  void aRedeclarationIsKeptAndALaterUseSeesTheLatestDeclaration() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.declare("x", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.declare("x", BindingKind.VARIABLE, at(OFFSET_10), Set.of());
    builder.reference("x", at(OFFSET_20), ReferenceAccess.READ);

    final UnitScopes scopes = builder.build();

    assertThat(scopes.module().bindings()).hasSize(2);
    assertThat(scopes.references().get(0).resolved().orElseThrow().declaration().startOffset())
        .isEqualTo(OFFSET_10);
  }

  @Test
  void skippedClassScopesAreInvisibleToNestedFunctionsButNotToTheBodyItself() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.open(ScopeKind.CLASS, at(OFFSET_0));
    builder.declare("member", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.reference("member", at(OFFSET_2), ReferenceAccess.READ);
    builder.open(ScopeKind.FUNCTION, at(OFFSET_3));
    builder.reference("member", at(OFFSET_4), ReferenceAccess.READ);

    final List<NameReference> references = builder.build().references();

    assertThat(references.get(0).resolved()).isPresent();
    assertThat(references.get(1).resolved()).isEmpty();
  }

  @Test
  void visibleClassScopesAreSeenByNestedFunctions() {
    final ScopeBuilder builder = builder(ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, at(OFFSET_0));
    builder.declare("member", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.open(ScopeKind.FUNCTION, at(OFFSET_3));
    builder.reference("member", at(OFFSET_4), ReferenceAccess.READ);

    assertThat(builder.build().references().get(0).resolved()).isPresent();
  }

  @Test
  void globalAndNonlocalAliasesSendTheLookupOutward() {
    final ScopeBuilder builder = builder(ClassScopes.SKIPPED);
    builder.declare("g", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.open(ScopeKind.FUNCTION, at(OFFSET_2));
    builder.declare("n", BindingKind.VARIABLE, at(OFFSET_3), Set.of());
    builder.open(ScopeKind.FUNCTION, at(OFFSET_4));
    builder.declare("g", BindingKind.VARIABLE, at(OFFSET_5), Set.of(BindingFlag.GLOBAL));
    builder.declare("n", BindingKind.VARIABLE, at(OFFSET_6), Set.of(BindingFlag.NONLOCAL));
    builder.reference("g", at(OFFSET_7), ReferenceAccess.WRITE);
    builder.reference("n", at(OFFSET_8), ReferenceAccess.WRITE);

    final List<NameReference> references = builder.build().references();

    assertThat(references.get(0).resolved().orElseThrow().scope()).isEqualTo(UnitScopes.MODULE_ID);
    assertThat(references.get(1).resolved().orElseThrow().declaration().startOffset())
        .isEqualTo(OFFSET_3);
  }
}
