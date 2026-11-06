package dev.ward.analysis.domain.python;

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

class PythonScopesTest {

  private static final int LINE_2 = 2;
  private static final int LINE_3 = 3;
  private static final int LINE_4 = 4;
  private static final int LINE_5 = 5;

  private static NameReference only(final UnitScopes scopes, final String name, final int line) {
    final List<NameReference> found =
        scopes.references().stream()
            .filter(reference -> reference.name().equals(name))
            .filter(reference -> reference.span().startLine() == line)
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  @Test
  void nonlocalWriteResolvesToTheEnclosingFunction() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            """
            def outer():
                count = 0
                def inner():
                    nonlocal count
                    count = 1
                return inner
            """);

    final NameReference write = only(scopes, "count", LINE_5);

    assertThat(write.access()).isEqualTo(ReferenceAccess.WRITE);
    assertThat(write.resolved()).isPresent();
    assertThat(write.resolved().get().declaration().startLine()).isEqualTo(2);
    assertThat(scopes.scopes().get(write.resolved().get().scope()).kind())
        .isEqualTo(ScopeKind.FUNCTION);
  }

  @Test
  void globalWriteDeclaresTheNameInTheModule() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            """
            def f():
                global flag
                flag = True
            print(flag)
            """);

    final Binding flag = only(scopes, "flag", LINE_4).resolved().orElseThrow();

    assertThat(scopes.scopes().get(flag.scope()).kind()).isEqualTo(ScopeKind.MODULE);
    assertThat(scopes.module().bindings()).extracting(Binding::name).contains("flag");
  }

  @Test
  void methodsDoNotSeeClassVariables() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            """
            class A:
                x = 1
                y = x
                def m(self):
                    return x
            """);

    assertThat(only(scopes, "x", LINE_3).resolved()).isPresent();
    assertThat(only(scopes, "x", LINE_5).resolved()).isEmpty();
  }

  @Test
  void comprehensionVariablesDoNotLeak() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes("xs = [1]\nys = [n for n in xs]\nprint(n)\n");

    assertThat(only(scopes, "n", LINE_3).resolved()).isEmpty();
    assertThat(only(scopes, "xs", LINE_2).resolved()).isPresent();
    assertThat(scopes.scopes()).extracting("kind").contains(ScopeKind.COMPREHENSION);
  }

  @Test
  void starImportOpensTheModuleScope() {
    final UnitScopes scopes = PythonScopeFixtures.scopes("from os import *\nprint(path)\n");

    final NameReference path = only(scopes, "path", LINE_2);

    assertThat(path.resolved()).isEmpty();
    assertThat(scopes.isOpen(path)).isTrue();
  }

  @Test
  void execOpensTheFunctionScopeOnly() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            """
            def f():
                exec("x = 1")
                return x
            def g():
                return y
            """);

    assertThat(scopes.isOpen(only(scopes, "x", LINE_3))).isTrue();
    assertThat(scopes.isOpen(only(scopes, "y", LINE_5))).isFalse();
  }

  @Test
  void importsRecordWhereTheNameComesFrom() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes("import a.b\nfrom .pkg import thing as other\n");

    final List<Binding> imports =
        scopes.module().bindings().stream()
            .filter(binding -> binding.kind() == BindingKind.IMPORT)
            .toList();

    assertThat(imports).extracting(Binding::name).containsExactly("a", "other");
    assertThat(imports.get(0).origin().orElseThrow().specifier()).isEqualTo("a.b");
    assertThat(imports.get(1).origin().orElseThrow().specifier()).isEqualTo(".pkg");
    assertThat(imports.get(1).origin().orElseThrow().member()).isEqualTo("thing");
  }

  @Test
  void fStringFieldsReadNames() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            "w = 3\nv = {'k': 1}\nprint(f\"{v['k']:>{w}} {{literal}} {v.keys()}\")\n");

    assertThat(scopes.references())
        .filteredOn(reference -> reference.access() == ReferenceAccess.READ)
        .extracting(NameReference::name)
        .contains("w", "v")
        .doesNotContain("k", "keys", "literal");
  }

  @Test
  void aWriteBeforeTheDeclarationInTheSameFunctionIsLocal() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes(
            """
            x = 1
            def f():
                print(x)
                x = 2
            """);

    final NameReference read = only(scopes, "x", LINE_3);

    assertThat(read.beforeDeclaration()).isTrue();
    assertThat(scopes.scopes().get(read.resolved().orElseThrow().scope()).kind())
        .isEqualTo(ScopeKind.FUNCTION);
  }

  @Test
  void definitionsAreDeclaredEachTimeAndAssignmentsOnce() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes("def f(): pass\ndef f(): pass\ny = 1\ny = 2\n");

    assertThat(scopes.module().bindings()).extracting(Binding::name).containsExactly("f", "f", "y");
  }

  @Test
  void unpackedTargetsAreFlaggedAndNamesInAllAreExported() {
    final UnitScopes scopes = PythonScopeFixtures.scopes("a, b = 1, 2\n__all__ = ['a']\n");

    assertThat(scopes.module().bindings())
        .filteredOn(binding -> binding.name().equals("a"))
        .allSatisfy(
            binding ->
                assertThat(binding.flags())
                    .contains(BindingFlag.DESTRUCTURED, BindingFlag.EXPORTED));
  }
}
