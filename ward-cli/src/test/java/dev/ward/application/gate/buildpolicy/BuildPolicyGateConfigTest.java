package dev.ward.application.gate.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildPolicyGateConfigTest {
  @Test
  void unknownOptionsExcludePathsAndUnselectedTablesAreConfigurationErrors() {
    final GateResult result =
        GateFixtures.run(
            GateFixtures.model(),
            GateFixtures.file(
                GateFixtures.select("none", "build.plugin-version-pinned", "build.nope"),
                Map.of(
                    "ward:build/plugin-version-pinned", Map.of("ignore", List.of("x")),
                    "ward:build/no-dynamic-version", Map.of("exclude-paths", List.of("legacy/**")),
                    "ward:build/minimum-build-tool-version", Map.of("maven", 3L),
                    "ward:build/bogus", Map.of())));
    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr())
        .contains("[rules.build.plugin-version-pinned] ignore")
        .contains("unknown rule \"build.nope\"")
        .contains("not selected")
        .contains("unknown rule id");
  }

  @Test
  void excludePathsOnASelectedRuleIsRefused() {
    final GateResult result =
        GateFixtures.run(
            GateFixtures.model(),
            GateFixtures.file(
                GateFixtures.select("none", "build.no-dynamic-version"),
                Map.of(
                    "ward:build/no-dynamic-version",
                    Map.of("exclude-paths", List.of("legacy/**")))));
    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("exclude-paths").contains("no suppression");
  }

  @Test
  void idsOfOtherGatesAreIgnored() {
    final GateResult result =
        GateFixtures.run(
            GateFixtures.model(),
            GateFixtures.file(
                GateFixtures.select("none", "build.no-dynamic-version", "java.method-length"),
                Map.of()));
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void severityWarningDoesNotFailTheCheck() {
    final GateResult result =
        GateFixtures.run(
            GateFixtures.model(),
            GateFixtures.file(
                GateFixtures.select("none", "build.no-dynamic-version"),
                Map.of("ward:build/no-dynamic-version", Map.of("severity", "warning"))));
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.warnings()).isEqualTo(1);
  }
}
