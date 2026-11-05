package dev.ward.application.doctor.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JavaTypeSidecarFindingTest {

  @Test
  void reportsTheSidecarAvailableWhenJavacIsOnThePath() {
    final Finding finding =
        JavaTypeSidecarFinding.finding(new EnvironmentFacts(Set.of("javac"), Map.of()));

    assertThat(finding.status()).isEqualTo(Status.OK);
    assertThat(finding.name()).isEqualTo("java-type-sidecar");
  }

  @Test
  void warnsOptionallyWhenJavacIsMissing() {
    final Finding finding =
        JavaTypeSidecarFinding.finding(new EnvironmentFacts(Set.of("java"), Map.of()));

    assertThat(finding.status()).isEqualTo(Status.WARNING);
    assertThat(finding.message()).contains("optional").contains("javac");
  }
}
