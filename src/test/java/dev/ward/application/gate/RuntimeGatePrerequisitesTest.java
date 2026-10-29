package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GatePrerequisite;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.init.CompatibilityInspection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeGatePrerequisitesTest {
  private static final int RELEASE = 25;
  private static final int OLDER_JDK = 17;
  private static final int COMPATIBLE_RELEASE = 21;
  private static final int NEWER_JDK = 25;

  @Test
  void runtimeOlderThanDeclaredReleaseIsUnavailable() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.jdk(RELEASE, OLDER_JDK, new AtomicInteger()),
                    GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.buildGate(
                            "mvn", GatePrerequisiteFixtures.failingBuild()))));

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.prerequisite().kind()).isEqualTo(GatePrerequisite.Kind.RUNTIME);
    assertThat(result.prerequisite().required()).isEqualTo("25");
    assertThat(result.prerequisite().detected()).isEqualTo("17");
    assertThat(result.summary()).contains("needs Java 25").contains("runs on Java 17");
  }

  @Test
  void compatibleRuntimeStaysFailed() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.jdk(
                        COMPATIBLE_RELEASE, NEWER_JDK, new AtomicInteger()),
                    GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.buildGate(
                            "mvn", GatePrerequisiteFixtures.failingBuild()))));

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.prerequisite()).isNull();
  }

  @Test
  void unknownRuntimeStaysFailed() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.jdk(RELEASE, null, new AtomicInteger()),
                    GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.buildGate(
                            "mvn", GatePrerequisiteFixtures.failingBuild()))));

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.prerequisite()).isNull();
  }

  @Test
  void inspectionFailureLeavesFailedResultUnchanged() {
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(
                    GatePrerequisiteFixtures.failing(), GatePrerequisiteFixtures.failing())
                .run(
                    GatePrerequisiteFixtures.MAVEN,
                    List.of(
                        GatePrerequisiteFixtures.buildGate(
                            "mvn", GatePrerequisiteFixtures.failingBuild()))));

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.prerequisite()).isNull();
  }

  @Test
  void passingRunNeverInspects() {
    final AtomicInteger calls = new AtomicInteger();
    GatePrerequisiteFixtures.runner(
            GatePrerequisiteFixtures.jdk(RELEASE, OLDER_JDK, calls),
            GatePrerequisiteFixtures.failing())
        .run(
            GatePrerequisiteFixtures.MAVEN,
            List.of(GatePrerequisiteFixtures.buildGate("mvn", new FakeCommandExecutor(0, "", ""))));

    assertThat(calls).hasValue(0);
  }

  @Test
  void twoFailingMavenGatesInspectOnce() {
    final AtomicInteger calls = new AtomicInteger();
    final List<QualityGate> gates =
        List.of(
            GatePrerequisiteFixtures.buildGate("mvn", GatePrerequisiteFixtures.failingBuild()),
            GatePrerequisiteFixtures.buildGate("mvn", GatePrerequisiteFixtures.failingBuild()));
    GatePrerequisiteFixtures.runner(
            GatePrerequisiteFixtures.jdk(COMPATIBLE_RELEASE, NEWER_JDK, calls),
            GatePrerequisiteFixtures.failing())
        .run(GatePrerequisiteFixtures.MAVEN, gates);

    assertThat(calls).hasValue(1);
  }

  @Test
  void gradleFailureUsesGradleInspection() {
    final AtomicInteger gradleCalls = new AtomicInteger();
    final CompatibilityInspection gradle =
        GatePrerequisiteFixtures.jdk(RELEASE, OLDER_JDK, gradleCalls);
    final GateResult result =
        GatePrerequisiteFixtures.only(
            GatePrerequisiteFixtures.runner(GatePrerequisiteFixtures.failing(), gradle)
                .run(
                    GatePrerequisiteFixtures.GRADLE,
                    List.of(
                        GatePrerequisiteFixtures.buildGate(
                            "gradle", GatePrerequisiteFixtures.failingBuild()))));

    assertThat(gradleCalls).hasValue(1);
    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
  }
}
