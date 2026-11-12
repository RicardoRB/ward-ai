package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code secrets} gate: SecretGateScanTest. */
final class SecretGateScanTest extends SecretGateTestSupport {

  @Test
  void reportsTheTokenWithRuleFileLineAndColumnAndNoSecret() throws IOException {
    write(CONFIG, TOKEN_LINE);

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = onlyFinding(result);
    assertThat(finding.ruleId()).isEqualTo("ward:secrets/github-pat");
    assertThat(finding.file()).isEqualTo(CONFIG);
    assertThat(finding.line()).isEqualTo(TOKEN_LINE_NUMBER);
    assertThat(finding.column()).isEqualTo(TOKEN_COLUMN);
    assertThat(result.toString())
        .doesNotContain(TOKEN)
        .doesNotContain(TOKEN.substring(0, PREFIX_LENGTH));
    assertThat(finding.toString()).doesNotContain("token:");
  }

  @Test
  void aCleanProjectPasses() {
    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void hiddenFoldersAreScanned() throws IOException {
    write(".github/workflows/deploy.yml", "env: " + TOKEN + "\n");

    assertThat(onlyFinding(run(SecretsOptions.NONE, List.of())).file())
        .isEqualTo(".github/workflows/deploy.yml");
  }

  @Test
  void buildOutputAndIgnoredFilesAreSkipped() throws IOException {
    write("target/out.txt", TOKEN_LINE);
    write("node_modules/pkg/a.txt", TOKEN_LINE);
    write("secret.env", TOKEN_LINE);
    ignored = "secret.env\u0000";

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aBinaryFileIsSkippedWithoutAnError() throws IOException {
    final byte[] bytes = (TOKEN_LINE + "\u0000").getBytes(StandardCharsets.ISO_8859_1);
    Files.write(root.resolve("blob.bin"), new byte[] {0, 1, 2});
    Files.write(root.resolve("blob.dat"), concat(new byte[] {0}, bytes));

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aLatin1FileIsScannedInsteadOfRaisingAnError() throws IOException {
    Files.createDirectories(root.resolve("config"));
    Files.write(
        root.resolve(CONFIG),
        ("café: 1\ntoken: " + TOKEN + "\n").getBytes(StandardCharsets.ISO_8859_1));

    assertThat(onlyFinding(run(SecretsOptions.NONE, List.of())).line()).isEqualTo(2);
  }

  @Test
  void onlyTheEditedFilesAreScanned() throws IOException {
    write(CONFIG, TOKEN_LINE);
    write("src/other.yml", TOKEN_LINE);

    final GateResult result = run(SecretsOptions.NONE, List.of("src/other.yml", "deleted.yml"));

    assertThat(onlyFinding(result).file()).isEqualTo("src/other.yml");
  }

  @Test
  void aGitleaksCommentIsNotASuppression() throws IOException {
    write(CONFIG, "token: " + TOKEN + " # gitleaks:allow\n");

    assertThat(run(SecretsOptions.NONE, List.of()).status()).isEqualTo(GateStatus.FAILED);
  }
}
