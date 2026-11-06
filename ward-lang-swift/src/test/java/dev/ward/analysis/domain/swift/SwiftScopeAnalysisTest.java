package dev.ward.analysis.domain.swift;

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

class SwiftScopeAnalysisTest {

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
  void aClosureParameterIsLocalToTheClosure() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("func f() {\n  let g = { (n: Int) -> Int in n + 1 }\n  n\n}\n");

    assertThat(only(scopes, "n", LINE_2).resolved()).isPresent();
    assertThat(
            scopes.scopes().get(only(scopes, "n", LINE_2).resolved().orElseThrow().scope()).kind())
        .isEqualTo(ScopeKind.LAMBDA);
    assertThat(only(scopes, "n", LINE_3).resolved()).isEmpty();
  }

  @Test
  void aClosureWithoutASignatureIsOpen() {
    final UnitScopes scopes = SwiftScopeFixtures.scopes("let f = { $0 + extra }\n");

    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("$0", "extra");
    assertThat(scopes.unresolved()).allMatch(scopes::isOpen);
  }

  @Test
  void guardLetBindsInTheEnclosingBlockAndIfLetOnlyInItsStatement() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes(
            """
            func f(a: Int?, b: Int?) {
              guard let x = a else { return }
              if let y = b { print(y) }
              print(x, y)
            }
            """);

    assertThat(only(scopes, "x", LINE_4).resolved()).isPresent();
    assertThat(only(scopes, "y", LINE_4).resolved()).isEmpty();
  }

  @Test
  void switchPatternsBindInTheirCase() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes(
            "func f(t: Int) {\n  switch t {\n  case let v: print(v)\n  default: print(v)\n  }\n}\n");

    assertThat(only(scopes, "v", LINE_3).resolved()).isPresent();
    assertThat(only(scopes, "v", LINE_4).resolved()).isEmpty();
  }

  @Test
  void membersAreVisibleInMethodsAndASubclassIsOpen() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes(
            "struct S { var a = 1\n func m() { a }\n}\nclass C: Base { func m() { b } }\n");

    assertThat(only(scopes, "a", LINE_2).resolved()).isPresent();
    assertThat(scopes.isOpen(only(scopes, "b", LINE_4))).isTrue();
  }

  @Test
  void aMemberNameAfterTheDotIsNotAReference() {
    final UnitScopes scopes = SwiftScopeFixtures.scopes("func f(p: P) { p.value.deep }\n");

    assertThat(scopes.references()).extracting(NameReference::name).containsExactly("p");
  }

  @Test
  void catchWithoutAPatternBindsError() {
    final UnitScopes scopes =
        SwiftScopeFixtures.scopes("func f() { do { try g() } catch { print(error) } }\n");

    assertThat(only(scopes, "error", LINE_1).resolved()).isPresent();
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
