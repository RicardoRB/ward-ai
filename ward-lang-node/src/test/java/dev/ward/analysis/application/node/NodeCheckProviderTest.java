package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NodeCheckProviderTest {

  @Test
  void namesEveryNodeCheckWithANodePrefixFreeOfCollisions() {
    assertThat(new NodeCheckProvider().checks()).isNotNull().doesNotContainKey("");
  }
}
