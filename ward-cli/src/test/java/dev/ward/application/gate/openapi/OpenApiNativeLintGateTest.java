package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiNativeLintGateTest {

  @TempDir Path root;

  private NativeLintFixture fixture() {
    return new NativeLintFixture(root);
  }

  @Test
  void aViolationFailsTheGateWithItsFileAndPosition() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.VIOLATING);

    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("ward:openapi/operation-operation-id");
              assertThat(finding.file()).isEqualTo(NativeLintFixture.SPEC);
              assertThat(finding.line() + ":" + finding.column()).isEqualTo("4:5");
            });
  }

  @Test
  void aCleanSpecPasses() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.CLEAN);

    assertThat(fixture().lint().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aMissingSpecFailsAndNamesTheFile() {
    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains(NativeLintFixture.SPEC);
  }

  @Test
  void aWarningFailsTheGateLikeAnError() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.VIOLATING);
    fixture()
        .write(
            "ward-rules.toml",
            "version = 1\n[rules.openapi.operation-operation-id]\nseverity = \"warning\"\n");

    final GateResult result = fixture().lint();

    assertThat(result.status()).as(result.stderr()).isEqualTo(GateStatus.FAILED);
    assertThat(result.warnings()).isEqualTo(1);
  }

  @Test
  void aRuleExcludedInTheRuleFileDoesNotRun() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.VIOLATING);
    fixture()
        .write(
            "ward-rules.toml",
            "version = 1\n[select]\nexclude = [\"openapi.operation-operation-id\"]\n");

    assertThat(fixture().lint().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aTableForAnUnknownRuleIsAConfigurationError() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.CLEAN);
    fixture()
        .write(
            "ward-rules.toml",
            "version = 1\n[rules.openapi.no-such-rule]\nseverity = \"warning\"\n");

    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("no-such-rule");
  }

  @Test
  void findingsInAReferencedFilePointAtThatFile() throws IOException {
    fixture()
        .write(NativeLintFixture.SPEC, "openapi: 3.0.3\npaths:\n  /pets:\n    $ref: 'pets.yaml'\n");
    fixture().write("api/pets.yaml", "get:\n  summary: x\n");

    final GateResult result = fixture().lint();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().file()).isEqualTo("api/pets.yaml");
  }

  @Test
  void aVacuumConfigFileIsNeitherReadNorRefused() throws IOException {
    fixture().write(NativeLintFixture.SPEC, NativeLintFixture.CLEAN);
    fixture().write("vacuum.conf.yaml", "ignore: all\n");

    assertThat(fixture().lint().status()).isEqualTo(GateStatus.PASSED);
  }
}
