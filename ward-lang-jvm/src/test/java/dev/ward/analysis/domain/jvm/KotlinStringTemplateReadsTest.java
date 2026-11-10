package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that names read in Kotlin string templates count as references of their bindings. */
class KotlinStringTemplateReadsTest {

  @Test
  void aDollarNameInATemplateReadsTheBinding() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin("fun f(): String {\n  val used = 1\n  return \"$used\"\n}\n");

    assertThat(scopes.references())
        .filteredOn(reference -> "used".equals(reference.name()))
        .allSatisfy(reference -> assertThat(reference.resolved()).isPresent());
    assertThat(scopes.references()).extracting(NameReference::name).contains("used");
    assertThat(accessesOf(scopes, "used")).contains(ReferenceAccess.READ);
    assertThat(scopes.scopes().stream().filter(Scope::open).count()).isZero();
  }

  @Test
  void aBracedExpressionInATemplateReadsEveryNameInIt() {
    final UnitScopes scopes =
        JvmScopeFixtures.kotlin(
            "fun g(): String {\n  val inner = 2\n  return \"${inner + 1}\"\n}\n");

    assertThat(scopes.references())
        .filteredOn(reference -> "inner".equals(reference.name()))
        .allSatisfy(reference -> assertThat(reference.resolved()).isPresent());
    assertThat(scopes.references()).extracting(NameReference::name).contains("inner");
    assertThat(accessesOf(scopes, "inner")).contains(ReferenceAccess.READ);
    assertThat(scopes.scopes().stream().filter(Scope::open).count()).isZero();
  }

  private static List<ReferenceAccess> accessesOf(final UnitScopes scopes, final String name) {
    return scopes.bindings().stream()
        .filter(binding -> name.equals(binding.name()))
        .flatMap(binding -> scopes.usesOf(binding).stream())
        .map(NameReference::access)
        .toList();
  }
}
