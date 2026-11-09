package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JSX tags and expression containers as scope references. */
class NodeJsxScopeTest {

  @Test
  void componentUsedOnlyAsTagIsAReference() {
    final UnitScopes scopes =
        NodeScopeFixtures.tsx(
            "import { Button } from \"./button\";\nexport const view = <Button />;\n");

    assertThat(readNames(scopes)).contains("Button");
  }

  @Test
  void lowerCaseIntrinsicTagIsNotAReference() {
    final UnitScopes scopes = NodeScopeFixtures.tsx("export const view = <div title=\"a\" />;\n");

    assertThat(readNames(scopes)).doesNotContain("div", "title");
  }

  @Test
  void memberTagReadsItsLeadingName() {
    final UnitScopes scopes = NodeScopeFixtures.tsx("export const view = <ui.Header />;\n");

    assertThat(readNames(scopes)).contains("ui").doesNotContain("Header");
  }

  @Test
  void identifierInsideExpressionContainerIsRead() {
    final UnitScopes scopes =
        NodeScopeFixtures.tsx("const label = 1;\nexport const view = <p>{label}</p>;\n");

    assertThat(readNames(scopes)).contains("label");
  }

  @Test
  void typeScriptCastStillReadsItsOperand() {
    final UnitScopes scopes = NodeScopeFixtures.typescript("const n = <number>value;\n");

    assertThat(readNames(scopes)).contains("value");
  }

  private static List<String> readNames(final UnitScopes scopes) {
    return scopes.references().stream()
        .filter(reference -> reference.access() == ReferenceAccess.READ)
        .map(NameReference::name)
        .toList();
  }
}
