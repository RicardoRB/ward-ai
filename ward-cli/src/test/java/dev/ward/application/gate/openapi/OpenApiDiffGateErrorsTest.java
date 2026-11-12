package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.Test;

class OpenApiDiffGateErrorsTest {
  private static final String DIFF = "openapi-diff";
  private static final String HEAD_SPEC =
      OpenApiGateFixtures.HEAD_SHA + ":" + OpenApiGateFixtures.SPEC;

  private static GateResult run(final OpenApiGateFixtures fixtures) {
    return fixtures.gate(DIFF, OpenApiGateFixtures.NO_COMMAND).execute(OpenApiGateFixtures.PROJECT);
  }

  @Test
  void aSpecThatDoesNotParseInTheWorkingTreeIsAnExecutionError() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put(OpenApiGateFixtures.SPEC, "openapi: [unclosed\n");

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("working tree");
  }

  @Test
  void aBaselineThatDoesNotParseIsAnExecutionError() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.committedContents.put(HEAD_SPEC, "openapi: [unclosed\n");

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(OpenApiGateFixtures.HEAD_SHA);
  }

  @Test
  void aReferenceMissingAtTheBaselineIsAnExecutionError() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    final String withReference =
        OpenApiGateFixtures.DOCUMENT + "components:\n  schemas:\n    Pet:\n      $ref: 'pet.yml'\n";
    fixtures.workingTree.put(OpenApiGateFixtures.SPEC, withReference);
    fixtures.workingTree.put("pet.yml", "type: object\n");
    fixtures.committedContents.put(HEAD_SPEC, withReference);

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("pet.yml");
  }

  @Test
  void aGitReadFailureIsAnExecutionError() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.unreadable = true;

    final GateResult result = run(fixtures);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("from Git");
  }

  @Test
  void aCommandIsRejectedWithAConfigurationError() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();

    assertThatThrownBy(() -> fixtures.gate(DIFF, java.util.List.of("oasdiff", "breaking")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("does not take a 'command'");
  }
}
