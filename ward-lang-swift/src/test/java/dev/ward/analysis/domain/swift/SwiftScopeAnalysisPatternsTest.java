package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwiftScopeAnalysisPatternsTest {

  private static final int LINE_1 = 1;
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
  void catchWithoutAPatternBindsError() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("func f() { do { try g() } catch { print(error) } }\n");

    assertThat(only(scopes, "error", LINE_1).resolved()).isPresent();
  }

  @Test
  void ifLetShorthandBindsTheSameName() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("func f(x: Int?) {\nif let x {\nprint(x)\n}\n}\n");

    assertThat(only(scopes, "x", LINE_3).resolved()).isPresent();
  }

  @Test
  void macroParametersStayInsideTheMacro() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes(
            "macro m(_ value: Int) = #externalMacro(module: \"M\", type: \"S\")\nfunc g() { print(value) }\n");

    assertThat(only(scopes, "value", LINE_2).resolved()).isEmpty();
  }

  @Test
  void compoundAssignmentReadsAndWrites() {
    final UnitScopes scopes = SwiftScopeFixtures.scopes("func f() { var n = 0\n n += 1 }\n");

    assertThat(scopes.references())
        .filteredOn(
            reference -> "n".equals(reference.name()) && reference.span().startLine() == LINE_2)
        .extracting(NameReference::access)
        .containsExactlyInAnyOrder(ReferenceAccess.READ, ReferenceAccess.WRITE);
  }

  @Test
  void importsBindTheModuleOrTheNamedMember() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("import Foundation\nimport struct Mod.Thing\n");

    assertThat(scopes.module().bindings())
        .extracting(Binding::name, Binding::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Foundation", BindingKind.IMPORT),
            org.assertj.core.groups.Tuple.tuple("Thing", BindingKind.IMPORT));
  }

  @Test
  void tuplePatternsAreDestructured() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("func f() { let (a, b) = pair\n for (k, v) in d {} }\n");

    assertThat(scopes.bindings())
        .filteredOn(binding -> List.of("a", "b", "k", "v").contains(binding.name()))
        .hasSize(LINE_4)
        .allSatisfy(binding -> assertThat(binding.has(BindingFlag.DESTRUCTURED)).isTrue());
  }
}
