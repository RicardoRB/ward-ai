package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The {@code secrets} gate: SecretGateErrorsTest. */
final class SecretGateErrorsTest extends SecretGateTestSupport {

  @Test
  void aTextFileOverTheLimitIsAnExecutionErrorNamingTheFile() throws IOException {
    writeLarge("big/data.txt");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("big/data.txt");
  }

  @Test
  void anUnreadableFileIsAnExecutionErrorNamingTheFile() throws IOException {
    write("locked/key.txt", "x\n");
    final Path file = root.resolve("locked/key.txt");
    Files.setPosixFilePermissions(
        file, java.util.EnumSet.noneOf(java.nio.file.attribute.PosixFilePermission.class));

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("locked/key.txt");
  }

  @Test
  void anExcludedOversizedFileRaisesNoError() throws IOException {
    writeLarge("big/data.txt");

    final GateResult result =
        run(new SecretsOptions(false, Optional.empty(), List.of("big/**")), List.of());

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void checkLevelExcludePathsHideEveryRule() throws IOException {
    write("fixtures/a.yml", TOKEN_LINE);

    assertThat(
            run(new SecretsOptions(false, Optional.empty(), List.of("fixtures/**")), List.of())
                .status())
        .isEqualTo(GateStatus.PASSED);
  }

  @Test
  void ruleExcludePathsAndEntropyComeFromTheRuleConfiguration() throws IOException {
    write("docs/setup.md", TOKEN_LINE);
    write(CONFIG, TOKEN_LINE);
    write(
        "ward-rules.toml",
        "version = 1\n[rules.secrets.github-pat]\nexclude-paths = [\"docs/**\"]\n");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(onlyFinding(result).file()).isEqualTo(CONFIG);
  }

  @Test
  void aNegativeEntropyIsAConfigurationErrorNamingTheRuleAndTheOption() throws IOException {
    write(CONFIG, TOKEN_LINE);
    write("ward-rules.toml", "version = 1\n[rules.secrets.github-pat]\nentropy = -1\n");

    final GateResult result = run(SecretsOptions.NONE, List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("github-pat").contains("entropy");
    assertThat(result.findings().items()).isEmpty();
  }
}
