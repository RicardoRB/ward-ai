package dev.ward.application.gate.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildPolicyGateTest {
  @Test
  void withoutAConfigurationFileTheStrictRulesRun() {
    final GateResult result = GateFixtures.run(GateFixtures.model(), null);
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("ward:build/no-dynamic-version");
              assertThat(finding.file()).isEqualTo("pom.xml");
              assertThat(finding.line()).isEqualTo(GateFixtures.DYNAMIC_LINE);
            });
  }

  @Test
  void aProjectWithNoBuildFileGetsAPass() {
    assertThat(GateFixtures.run(root -> BuildModel.EMPTY, null).status())
        .isEqualTo(GateStatus.PASSED);
  }

  @Test
  void strictPresetSelectsNoConvergenceAndStandardSelectsNothing() {
    assertThat(
            GateFixtures.run(
                    GateFixtures.model(),
                    GateFixtures.file(GateFixtures.select("standard"), Map.of()))
                .findings()
                .total())
        .isZero();
    assertThat(
            GateFixtures.run(
                    GateFixtures.model(),
                    GateFixtures.file(GateFixtures.select("strict"), Map.of()))
                .findings()
                .total())
        .isEqualTo(1);
  }

  @Test
  void anUnusableBuildFileIsAnExecutionErrorNamingTheFile() {
    final GateResult result =
        GateFixtures.run(
            root -> {
              throw new BuildFileException("pom.xml", "the file is not well-formed XML");
            },
            null);
    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("pom.xml");
  }
}
