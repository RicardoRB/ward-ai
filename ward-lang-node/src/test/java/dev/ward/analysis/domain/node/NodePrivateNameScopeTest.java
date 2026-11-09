package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.scope.ScopeDump;
import org.junit.jupiter.api.Test;

/**
 * Private names are scoped the way JavaScript scopes them: TypeScript and JavaScript produce the
 * same scopes for the same class. Neither language declares {@code #count} as a member name yet.
 */
class NodePrivateNameScopeTest {

  private static final String CLASS_WITH_PRIVATE_FIELD =
      "class Counter { #count = 0; read() { return this.#count; } }\n";

  @Test
  void typeScriptPrivateNamesScopeLikeJavaScript() {
    final String typeScript =
        ScopeDump.of(
            NodeScopeFixtures.scopes(AnalysisLanguage.TYPESCRIPT, CLASS_WITH_PRIVATE_FIELD));
    final String javaScript =
        ScopeDump.of(
            NodeScopeFixtures.scopes(AnalysisLanguage.JAVASCRIPT, CLASS_WITH_PRIVATE_FIELD));

    assertThat(typeScript).isEqualTo(javaScript);
  }
}
