package dev.ward.infrastructure.dependency.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TgfNodeTest {
  @Test
  void readsTheVersionThatDependencyManagementOverrode() {
    final TgfNode kept =
        TgfNode.parse(
            "org.apache.commons:commons-lang3:jar:3.0:compile (version managed from 3.12.0)");
    assertThat(kept.version()).isEqualTo("3.0");
    assertThat(kept.premanaged()).isEqualTo("3.12.0");

    final TgfNode omitted =
        TgfNode.parse(
            "(org.apache.commons:commons-lang3:jar:3.0:compile - version managed from 3.12.0;"
                + " omitted for duplicate)");
    assertThat(omitted.omitted()).isTrue();
    assertThat(omitted.premanaged()).isEqualTo("3.12.0");
  }

  @Test
  void aNodeWithoutManagementHasNoPremanagedVersion() {
    assertThat(TgfNode.parse("g:a:jar:1.0:compile").premanaged()).isEmpty();
  }
}
