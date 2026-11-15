package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleCapabilityTest {

  @Test
  void onlyTaintReadsTheBodiesOfTheFilesAroundItsUnit() {
    for (final RuleCapability capability : RuleCapability.values()) {
      assertThat(capability.readsDependencyBodies()).isEqualTo(capability == RuleCapability.TAINT);
    }
  }

  @Test
  void taintReadsDependenciesLikeSymbolsAndScopes() {
    assertThat(RuleCapability.TAINT.readsDependencies()).isTrue();
    assertThat(RuleCapability.SYMBOLS.readsDependencies()).isTrue();
    assertThat(RuleCapability.DATAFLOW.readsDependencies()).isFalse();
  }
}
