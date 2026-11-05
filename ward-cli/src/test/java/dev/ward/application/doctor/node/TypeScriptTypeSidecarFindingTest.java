package dev.ward.application.doctor.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeScriptTypeSidecarFindingTest {

  @Test
  void reportsTheSidecarAvailableWhenNodeAndTypescriptResolve() {
    final Finding finding =
        TypeScriptTypeSidecarFinding.finding(
            new EnvironmentFacts(Set.of("node"), Map.of("typescriptResolvable", "true")));

    assertThat(finding.status()).isEqualTo(Status.OK);
  }

  @Test
  void warnsThatTypeRulesAreSkippedWhenTypescriptIsMissing() {
    final Finding finding =
        TypeScriptTypeSidecarFinding.finding(
            new EnvironmentFacts(Set.of("node"), Map.of("typescriptResolvable", "false")));

    assertThat(finding.status()).isEqualTo(Status.WARNING);
    assertThat(finding.message()).contains("optional").contains("typescript");
  }

  @Test
  void warnsWhenNodeIsMissing() {
    final Finding finding =
        TypeScriptTypeSidecarFinding.finding(
            new EnvironmentFacts(Set.of(), Map.of("typescriptResolvable", "true")));

    assertThat(finding.status()).isEqualTo(Status.WARNING);
    assertThat(finding.message()).contains("node");
  }
}
