package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeScopeAnalysisTest {

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
  void aBlockReadBeforeItsLetResolvesToTheBlockBindingInTheDeadZone() {
    final UnitScopes scopes = NodeScopeFixtures.javascript("{\n  x;\n  let x = 1;\n}\n");

    final NameReference read = only(scopes, "x", 2, ReferenceAccess.READ);

    assertThat(read.beforeDeclaration()).isTrue();
    assertThat(scopes.scopes().get(read.resolved().orElseThrow().scope()).kind())
        .isEqualTo(ScopeKind.BLOCK);
  }

  @Test
  void varHoistsToTheFunctionAndLetStaysInTheBlock() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript(
            "function f() {\n  if (a) { var v = 1; let l = 2; }\n  return v + l;\n}\n");

    assertThat(only(scopes, "v", 3, ReferenceAccess.READ).resolved()).isPresent();
    assertThat(only(scopes, "l", 3, ReferenceAccess.READ).resolved()).isEmpty();
  }

  @Test
  void functionDeclarationsAreHoistedAndNotFlaggedAsBeforeDeclaration() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript("run();\nfunction run() {}\nuse;\nlet use = 1;\n");

    assertThat(only(scopes, "run", 1, ReferenceAccess.READ).beforeDeclaration()).isFalse();
    assertThat(only(scopes, "use", 3, ReferenceAccess.READ).beforeDeclaration()).isTrue();
  }

  @Test
  void aFunctionNameIsOnlyVisibleInsideItsOwnExpression() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript("const f = function inner() { return inner; };\ninner;\n");

    assertThat(only(scopes, "inner", 1, ReferenceAccess.READ).resolved()).isPresent();
    assertThat(only(scopes, "inner", 2, ReferenceAccess.READ).resolved()).isEmpty();
  }

  @Test
  void destructuringDeclaresTheValueNamesAndReadsDefaultsAndKeys() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript("const { a: renamed, b = fallback, ...others } = src;\n");

    assertThat(scopes.module().bindings())
        .extracting(Binding::name)
        .containsExactlyInAnyOrder("renamed", "b", "others");
    assertThat(scopes.unresolved()).extracting(NameReference::name).contains("fallback", "src");
    assertThat(scopes.unresolved()).extracting(NameReference::name).doesNotContain("a");
  }

  @Test
  void memberNamesAndPropertyKeysAreNotReferences() {
    final UnitScopes scopes =
        NodeScopeFixtures.javascript("const o = { key: 1, [calc]: 2 };\no.member.deep = 3;\n");

    assertThat(scopes.references())
        .extracting(NameReference::name)
        .doesNotContain("key", "member", "deep");
    assertThat(scopes.references()).extracting(NameReference::name).contains("calc", "o");
  }

  @Test
  void anUndeclaredAssignmentWritesAnUnresolvedName() {
    final UnitScopes scopes = NodeScopeFixtures.javascript("total = 1;\ntotal += 2;\n");

    assertThat(scopes.unresolved())
        .extracting(NameReference::access)
        .contains(ReferenceAccess.WRITE, ReferenceAccess.READ);
  }

  @Test
  void aCatchParameterBelongsToTheCatchScope() {
    final UnitScopes scopes = NodeScopeFixtures.javascript("try {} catch (e) { e; }\ne;\n");

    assertThat(only(scopes, "e", 1, ReferenceAccess.READ).resolved()).isPresent();
    assertThat(only(scopes, "e", 2, ReferenceAccess.READ).resolved()).isEmpty();
  }

  @Test
  void evalOpensTheScope() {
    final UnitScopes scopes = NodeScopeFixtures.javascript("function f() { eval(s); return g; }\n");

    assertThat(scopes.isOpen(only(scopes, "g", 1, ReferenceAccess.READ))).isTrue();
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
}
