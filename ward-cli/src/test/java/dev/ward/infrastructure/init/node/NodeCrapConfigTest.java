package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import org.junit.jupiter.api.Test;

/** Ward's own Vitest config lists the lcov reporter only when the crap gate reads it. */
class NodeCrapConfigTest {
  @Test
  void vitestConfigListsTextAndLcovWhenCrapIsSelected() {
    assertThat(NodeConfigTemplates.vitest(QualityPreset.STANDARD, true))
        .contains("provider: \"v8\",\n      reporter: [\"text\", \"lcov\"],\n      thresholds: {");
  }

  @Test
  void vitestConfigIsUnchangedWithoutCrap() {
    assertThat(NodeConfigTemplates.vitest(QualityPreset.STANDARD, false))
        .isEqualTo(NodeConfigTemplates.vitest(QualityPreset.STANDARD))
        .doesNotContain("reporter");
  }
}
