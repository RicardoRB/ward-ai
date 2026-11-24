package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.port.ControlFlowResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeContextualKeywordFunctionNameTest {
  @Test
  @DisplayName("a function named with a contextual keyword is lowered")
  void aFunctionNamedGlobalIsLowered() {
    // Given
    final String source = "export const a = 1;\nexport function global() {\n  process.env.X = '0';\n}\n";

    // When
    final ControlFlowResult result = NodeFlowFixtures.lower(source);

    // Then
    assertThat(NodeFlowFixtures.names(result)).contains("src/a#global");
  }
}
