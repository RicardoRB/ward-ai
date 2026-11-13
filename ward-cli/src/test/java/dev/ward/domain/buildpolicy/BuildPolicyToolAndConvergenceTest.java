package dev.ward.domain.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildPolicyToolAndConvergenceTest {
  @Test
  void everyRuleHasAnImplementation() {
    for (final BuildRule rule : BuildRule.values()) {
      assertThat(BuildPolicyRules.forRule(rule)).isNotNull();
      assertThat(rule.id()).isEqualTo("ward:build/" + rule.ruleName());
    }
  }

  @Test
  void minimumBuildToolVersionDefaultsMavenOnly() {
    final BuildModel model =
        new BuildModel(
            List.of(),
            List.of(
                new ToolVersion("maven", "3.8.8", "w", 1),
                new ToolVersion("maven", "3.9.9", "w", 1),
                new ToolVersion("gradle", "1.0", "g", 1)));
    assertThat(RuleModels.run(BuildRule.MINIMUM_BUILD_TOOL_VERSION, model))
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("3.8.8").contains("3.9"));
    final List<BuildPolicyFinding> custom =
        BuildPolicyRules.forRule(BuildRule.MINIMUM_BUILD_TOOL_VERSION)
            .check(new BuildPolicyInput(model, () -> null, Map.of("gradle", "8.5")));
    assertThat(custom).hasSize(2);
  }

  @Test
  void versionsCompareNumerically() {
    assertThat(Versions.compare("3.10", "3.9")).isPositive();
    assertThat(Versions.compare("3.9", "3.9.0")).isZero();
    assertThat(Versions.compare("3.8.8", "3.9")).isNegative();
  }
}
