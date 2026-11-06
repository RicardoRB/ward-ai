package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class JvmScopeAnalysisTest {

  private static final int LINE_2 = 2;
  private static final int LINE_3 = 3;
  private static final int LINE_4 = 4;

  private static NameReference only(final UnitScopes scopes, final String name, final int line) {
    final List<NameReference> found =
        scopes.references().stream()
            .filter(reference -> reference.name().equals(name))
            .filter(reference -> reference.span().startLine() == line)
            .filter(reference -> reference.access() == ReferenceAccess.READ)
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  @Test
  void aLocalShadowsAFieldInTheMethodOnly() {
    final UnitScopes scopes =
        JvmScopeFixtures.java(
            """
            class A {
              int x;
              int get() { int x = 1; return x; }
              int other() { return x; }
            }
            """);

    final Binding inMethod = only(scopes, "x", LINE_3).resolved().orElseThrow();
    final Binding inOther = only(scopes, "x", LINE_4).resolved().orElseThrow();

    assertThat(scopes.scopes().get(inMethod.scope()).kind()).isEqualTo(ScopeKind.BLOCK);
    assertThat(scopes.scopes().get(inOther.scope()).kind()).isEqualTo(ScopeKind.CLASS);
  }

  @Test
  void aLambdaSeesTheEnclosingLocalsAndOwnsItsParameters() {
    final UnitScopes scopes =
        JvmScopeFixtures.java(
            "class A { void m(int k) { java.util.function.Function<Integer,Integer> f = x -> x + k; x; } }\n");

    assertThat(scopes.references())
        .filteredOn(
            reference -> "x".equals(reference.name()) && reference.access() == ReferenceAccess.READ)
        .extracting(reference -> reference.resolved().isPresent())
        .containsExactly(true, false);
  }

  @Test
  void anInheritingClassIsOpenAndAPlainOneIsNot() {
    final UnitScopes scopes =
        JvmScopeFixtures.java(
            "class A extends B { void m() { inherited(); a = 1; } }\nclass C { void m() { c = 1; } }\n");

    final List<NameReference> unresolved = scopes.unresolved();

    assertThat(unresolved).extracting(NameReference::name).containsExactly("a", "c");
    assertThat(scopes.isOpen(unresolved.get(0))).isTrue();
    assertThat(scopes.isOpen(unresolved.get(1))).isFalse();
  }

  @Test
  void aStaticWildcardImportOpensTheFile() {
    final UnitScopes scopes =
        JvmScopeFixtures.java(
            "import static java.lang.Math.*;\nclass A { int m() { return max(1, 2) + z; } }\n");

    assertThat(scopes.isOpen(only(scopes, "z", LINE_2))).isTrue();
  }

  @Test
  void importsAndForEachVariablesAreRecorded() {
    final UnitScopes scopes =
        JvmScopeFixtures.java(
            "import java.util.List;\nclass A { void m(List<String> l) { for (String s : l) {} } }\n");

    assertThat(scopes.module().bindings())
        .extracting(Binding::name, Binding::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("List", BindingKind.IMPORT),
            org.assertj.core.groups.Tuple.tuple("A", BindingKind.CLASS));
    assertThat(scopes.bindings())
        .filteredOn(binding -> "s".equals(binding.name()))
        .allSatisfy(binding -> assertThat(binding.has(BindingFlag.DESTRUCTURED)).isTrue());
  }

  @Test
  void aKotlinLambdaWithoutParametersHasItAndIsOpen() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin(
            "fun f(xs: List<Int>) { xs.map { it + 1 }; xs.map { y -> y + z } }\n");

    assertThat(scopes.references())
        .filteredOn(reference -> "it".equals(reference.name()))
        .allSatisfy(reference -> assertThat(reference.resolved()).isPresent());
    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("z");
  }

  @Test
  void aKotlinMemberNameAfterTheDotIsNotAReference() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin("class A { val size = 1 }\nfun f(a: A) = a.size + a.other()\n");

    assertThat(scopes.references()).extracting(NameReference::name).doesNotContain("other");
    assertThat(scopes.references())
        .filteredOn(reference -> "a".equals(reference.name()))
        .allSatisfy(reference -> assertThat(reference.resolved()).isPresent());
  }

  @Test
  void aKotlinCompanionOpensItsClass() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin(
            "class A {\n  fun m() = LIMIT\n  companion object { const val LIMIT = 1 }\n}\n");

    assertThat(scopes.isOpen(only(scopes, "LIMIT", LINE_2))).isTrue();
  }

  @Test
  void kotlinParametersPropertiesAndLocalsAreSeparate() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin(
            "class P(val a: Int, b: Int) { fun m(c: Int): Int { val d = c; var e = 1; e++; return d } }\n");

    assertThat(scopes.bindings())
        .extracting(Binding::name, Binding::kind)
        .contains(
            org.assertj.core.groups.Tuple.tuple("a", BindingKind.CONSTANT),
            org.assertj.core.groups.Tuple.tuple("b", BindingKind.PARAMETER),
            org.assertj.core.groups.Tuple.tuple("c", BindingKind.PARAMETER),
            org.assertj.core.groups.Tuple.tuple("d", BindingKind.CONSTANT),
            org.assertj.core.groups.Tuple.tuple("e", BindingKind.VARIABLE));
  }
}
