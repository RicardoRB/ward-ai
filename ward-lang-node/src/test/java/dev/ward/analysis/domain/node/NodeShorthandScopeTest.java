package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks that a shorthand property {@code { id }} reads or writes the variable it names in both
 * grammars, so the unused, undefined and reassignment rules see the use.
 */
class NodeShorthandScopeTest {

  private static final int LINE_2 = 2;

  private static List<UnitScopes> bothLanguages(final String source) {
    return List.of(NodeScopeFixtures.typescript(source), NodeScopeFixtures.javascript(source));
  }

  private static List<NameReference> named(final UnitScopes scopes, final String name) {
    return scopes.references().stream().filter(reference -> reference.name().equals(name)).toList();
  }

  @Test
  void aShorthandPropertyReadsTheParameterItNamesInBothLanguages() {
    final String source = "function f(id, total) {\n  return { id };\n}\n";

    for (final UnitScopes scopes : bothLanguages(source)) {
      final List<NameReference> reads =
          named(scopes, "id").stream()
              .filter(reference -> reference.span().startLine() == LINE_2)
              .toList();

      assertThat(reads)
          .singleElement()
          .satisfies(
              read -> {
                assertThat(read.access()).isEqualTo(ReferenceAccess.READ);
                assertThat(read.resolved()).isPresent();
                assertThat(scopes.scopes().get(read.resolved().orElseThrow().scope()).kind())
                    .isEqualTo(ScopeKind.FUNCTION);
              });
    }
  }

  @Test
  void anUnusedParameterStaysWithoutAReadWhenNoShorthandNamesIt() {
    final String source = "function f(id, total) {\n  return { id };\n}\n";

    for (final UnitScopes scopes : bothLanguages(source)) {
      assertThat(named(scopes, "total"))
          .extracting(NameReference::access)
          .doesNotContain(ReferenceAccess.READ);
    }
  }

  @Test
  void aShorthandAssignmentTargetWritesTheVariableItNames() {
    final String source = "let a = 1;\n({ a } = src);\n";

    for (final UnitScopes scopes : bothLanguages(source)) {
      assertThat(named(scopes, "a").stream().filter(r -> r.span().startLine() == LINE_2))
          .singleElement()
          .satisfies(write -> assertThat(write.access()).isEqualTo(ReferenceAccess.WRITE));
    }
  }

  @Test
  void aShorthandDeclarationPatternOnlyWritesTheNameItDeclares() {
    final String source = "function f(src) {\n  const { id } = src;\n  return 1;\n}\n";

    for (final UnitScopes scopes : bothLanguages(source)) {
      assertThat(named(scopes, "id"))
          .extracting(NameReference::access)
          .containsExactly(ReferenceAccess.WRITE);
    }
  }

  @Test
  void anUndeclaredShorthandNameIsAnUnresolvedReadInTypeScript() {
    final UnitScopes scopes = NodeScopeFixtures.typescript("const shape = { missing };\n");

    assertThat(scopes.unresolved())
        .filteredOn(reference -> "missing".equals(reference.name()))
        .singleElement()
        .satisfies(reference -> assertThat(reference.access()).isEqualTo(ReferenceAccess.READ));
  }
}
