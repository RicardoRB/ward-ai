package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.Test;

class ApiCompatGateKotlinTest {
  private static final String PATH = "src/main/kotlin/p/Client.kt";
  private static final String CLIENT =
      "package p\n\nclass Client {\n  fun close() {}\n  internal fun hidden() {}\n}\n";

  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  @Test
  void unchangedKotlinApiPasses() {
    fixtures.baseline(PATH, CLIENT);
    fixtures.working.put(PATH, CLIENT);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("v1.0.0").contains("1 API types");
  }

  @Test
  void aRemovedKotlinFunctionFailsAndAnInternalOneDoesNot() {
    fixtures.baseline(PATH, CLIENT);
    fixtures.working.put(PATH, "package p\n\nclass Client\n");

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items()).hasSize(1);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("api-compat:member-removed");
    assertThat(finding.message()).contains("p.Client#close");
    assertThat(finding.file()).isEqualTo(PATH);
  }

  @Test
  void javaAndKotlinSourcesAreComparedTogether() {
    fixtures.baseline(PATH, CLIENT);
    fixtures.baseline(
        ApiCompatGateFixtures.CLIENT_PATH,
        "package q;\npublic class J {\n  public void a() {}\n}\n");
    fixtures.working.put(PATH, CLIENT);
    fixtures.working.put(ApiCompatGateFixtures.CLIENT_PATH, "package q;\npublic class J {}\n");

    final GateResult result = fixtures.run();

    assertThat(result.findings().items())
        .extracting(Finding::message)
        .singleElement()
        .asString()
        .contains("q.J#a");
  }

  @Test
  void aKotlinSyntaxErrorIsAnExecutionError() {
    fixtures.baseline(PATH, CLIENT);
    fixtures.working.put(PATH, "package p\n\nclass Client {\n  fun (\n}\n");

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.EXECUTION_ERROR);
  }
}
