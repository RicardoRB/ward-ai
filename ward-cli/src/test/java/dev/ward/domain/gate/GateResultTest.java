package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GateResultTest {
  private static final GatePrerequisite PREREQUISITE =
      new GatePrerequisite(
          GatePrerequisite.Kind.EXECUTABLE,
          "trivy",
          null,
          null,
          "Install trivy or add it to PATH.");

  private static GateResult unavailable() {
    return GateResult.builder("trivy", GateStatus.UNAVAILABLE)
        .command(List.of("trivy", "detect"))
        .build();
  }

  @Test
  void builderLeavesPrerequisiteUnset() {
    assertThat(unavailable().prerequisite()).isNull();
  }

  @Test
  void withPrerequisiteAttachesTheDiagnostic() {
    assertThat(unavailable().withPrerequisite(PREREQUISITE).prerequisite()).isEqualTo(PREREQUISITE);
  }

  @Test
  void withFindingsKeepsPrerequisite() {
    final GateResult result =
        unavailable().withPrerequisite(PREREQUISITE).withFindings(GateFindings.NONE);

    assertThat(result.prerequisite()).isEqualTo(PREREQUISITE);
  }

  @Test
  void withScopeKeepsPrerequisite() {
    final GateResult result = unavailable().withPrerequisite(PREREQUISITE).withScope(null);

    assertThat(result.prerequisite()).isEqualTo(PREREQUISITE);
  }
}
