package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class PythonScopeBindingsTest {

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
  void definitionsAreDeclaredEachTimeAndAssignmentsOnce() {
    final UnitScopes scopes =
        PythonScopeFixtures.scopes("def f(): pass\ndef f(): pass\ny = 1\ny = 2\n");

    assertThat(scopes.module().bindings()).extracting(Binding::name).containsExactly("f", "f", "y");
  }

  @Test
  void unpackedTargetsAreFlaggedAndNamesInAllAreExported() {
    final UnitScopes scopes = PythonScopeFixtures.scopes("a, b = 1, 2\n__all__ = ['a']\n");

    assertThat(scopes.module().bindings())
        .filteredOn(binding -> "a".equals(binding.name()))
        .allSatisfy(
            binding ->
                assertThat(binding.flags())
                    .contains(BindingFlag.DESTRUCTURED, BindingFlag.EXPORTED));
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
}
