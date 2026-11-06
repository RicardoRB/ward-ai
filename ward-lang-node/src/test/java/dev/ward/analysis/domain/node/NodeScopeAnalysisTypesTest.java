package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeScopeAnalysisTypesTest {

  private static final int LINE_1 = 1;

  private static NameReference only(
      final UnitScopes scopes, final String name, final int line, final ReferenceAccess access) {
    final List<NameReference> found =
        scopes.references().stream()
            .filter(reference -> reference.name().equals(name))
            .filter(reference -> reference.span().startLine() == line)
            .filter(reference -> reference.access() == access)
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  @Test
  void typeScriptTypeOnlyImportsBindTypeOnlyNames() {
    final UnitScopes scopes =
        NodeScopeFixtures.scopes(
            AnalysisLanguage.TYPESCRIPT,
            "import type { T } from './t';\nimport { type U, V } from './u';\nlet x: T = new V();\n");

    assertThat(scopes.module().bindings())
        .filteredOn(binding -> binding.kind() == BindingKind.IMPORT)
        .extracting(Binding::name, binding -> binding.has(BindingFlag.TYPE_ONLY))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("T", true),
            org.assertj.core.groups.Tuple.tuple("U", true),
            org.assertj.core.groups.Tuple.tuple("V", false));
    assertThat(scopes.unresolved()).isEmpty();
  }

  @Test
  void typeScriptGenericsAndInterfacesDeclareTypeBindings() {
    final UnitScopes scopes =
        NodeScopeFixtures.scopes(
            AnalysisLanguage.TYPESCRIPT,
            "interface Box<T> { value: T }\nfunction id<R>(x: R): R { return x; }\n");

    assertThat(scopes.bindings())
        .filteredOn(binding -> binding.kind() == BindingKind.TYPE)
        .extracting(Binding::name)
        .containsExactlyInAnyOrder("Box", "T", "R");
    assertThat(scopes.unresolved()).isEmpty();
  }

  @Test
  void exportedDeclarationsAreFlagged() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript("export const a = 1;\nexport function b() {}\nconst c = 2;\n");

    assertThat(scopes.module().bindings())
        .filteredOn(binding -> binding.has(BindingFlag.EXPORTED))
        .extracting(Binding::name)
        .containsExactly("a", "b");
  }

  @Test
  void evalOpensTheScope() {
    final UnitScopes scopes = NodeScopeFixtures.javascript("function f() { eval(s); return g; }\n");

    assertThat(scopes.isOpen(only(scopes, "g", LINE_1, ReferenceAccess.READ))).isTrue();
  }
}
