package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A destructured catch parameter declares the names it binds, so later reads resolve. */
class NodeCatchPatternScopeTest {

  @Test
  void objectPatternNamesResolveInsideTheCatchBlock() {
    final UnitScopes scopes =
        NodeScopeFixtures.typescript(
            "function run() { try { work(); } catch ({ message }) { log(message); } }\n");

    assertThat(readOf(scopes, "message").resolved()).isPresent();
  }

  @Test
  void arrayPatternNamesResolveInsideTheCatchBlock() {
    final UnitScopes scopes =
        NodeScopeFixtures.typescript(
            "function run() { try { work(); } catch ([first]) { log(first); } }\n");

    assertThat(readOf(scopes, "first").resolved()).isPresent();
  }

  private static NameReference readOf(final UnitScopes scopes, final String name) {
    final Optional<NameReference> read =
        scopes.references().stream()
            .filter(reference -> name.equals(reference.name()))
            .filter(reference -> reference.access() == ReferenceAccess.READ)
            .findFirst();
    return read.orElseThrow();
  }
}
