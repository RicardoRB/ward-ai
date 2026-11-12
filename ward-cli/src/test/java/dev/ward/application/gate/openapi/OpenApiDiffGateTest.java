package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.Test;

class OpenApiDiffGateTest {
  private static final String DIFF = "openapi-diff";

  private static GateResult run(final OpenApiGateFixtures fixtures) {
    return fixtures.gate(DIFF, OpenApiGateFixtures.NO_COMMAND).execute(OpenApiGateFixtures.PROJECT);
  }

  @Test
  void identicalSpecsPass() {
    final GateResult result = run(OpenApiGateFixtures.committedSpec());

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).isEqualTo("No breaking changes.");
  }

  @Test
  void aRemovedOperationFailsTheGateWithAFindingInTheWorkingTreeSpec() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put(
        OpenApiGateFixtures.SPEC,
        OpenApiGateFixtures.DOCUMENT.replace("    post:\n", "    put:\n"));

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("1 breaking change.");
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("ward:openapi/api-removed-without-deprecation");
    assertThat(finding.file()).isEqualTo(OpenApiGateFixtures.SPEC);
    assertThat(finding.line()).isPositive();
    assertThat(finding.message()).contains("POST /pets");
  }

  @Test
  void aRemovedPathIsReportedWithItsOwnRule() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put(
        OpenApiGateFixtures.SPEC, OpenApiGateFixtures.DOCUMENT.replace("/pets:", "/animals:"));

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .extracting(Finding::ruleId)
        .contains("ward:openapi/api-path-removed-without-deprecation");
  }

  @Test
  void specNotYetCommittedPassesWithoutBaseline() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.committedFiles.clear();

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("No baseline").contains("new");
  }

  @Test
  void repositoryWithoutCommitsPassesWithoutBaseline() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.revisions.clear();

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("no commits");
  }

  @Test
  void missingSpecFailsTheGate() {
    final GateResult result = run(new OpenApiGateFixtures());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("does not exist");
  }
}
