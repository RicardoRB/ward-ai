package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Ward's own Vitest config writes the text and lcov reports and holds no threshold. */
class NodeCrapConfigTest {
  @Test
  void vitestConfigListsTextAndLcovWithoutAThreshold() {
    assertThat(NodeConfigTemplates.vitest())
        .contains("provider: \"v8\",\n      reporter: [\"text\", \"lcov\"],")
        .doesNotContain("threshold");
  }
}
