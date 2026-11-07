package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import org.junit.jupiter.api.Test;

class NodeScopeTypeScriptNamesTest {

  @Test
  void aTypeScriptReadInAnInitializerIsAnUnresolvedReference() {
    final UnitScopes scopes = NodeScopeFixtures.typescript("const total = unknownName + 1;\n");

    assertThat(scopes.references().stream().map(NameReference::name)).contains("unknownName");
    final NameReference read =
        scopes.references().stream()
            .filter(reference -> reference.name().equals("unknownName"))
            .findFirst()
            .orElseThrow();
    assertThat(read.access()).isEqualTo(ReferenceAccess.READ);
    assertThat(read.resolved()).isEmpty();
  }
}
