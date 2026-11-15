package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code secrets} gate: SecretGateCacheTest. */
final class SecretGateCacheTest extends SecretGateTestSupport {

  @Test
  void aSecondRunReadsTheCacheAndAChangedFileRunsAgain() throws IOException {
    write(CONFIG, TOKEN_LINE);
    final GateResult first = run(SecretsOptions.NONE, List.of());
    final int stored = cache.size();

    final GateResult second = run(SecretsOptions.NONE, List.of());
    write(CONFIG, "clean\n");
    final GateResult third = run(SecretsOptions.NONE, List.of());

    assertThat(stored).isPositive();
    assertThat(second.findings().items()).isEqualTo(first.findings().items());
    assertThat(third.status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void aChangedEntropyOptionRunsTheFileAgainThroughTheCache() throws IOException {
    write(CONFIG, TOKEN_LINE);
    assertThat(onlyFinding(run(SecretsOptions.NONE, List.of())).line())
        .isEqualTo(TOKEN_LINE_NUMBER);

    write("ward-rules.toml", "version = 1\n[rules.secrets.github-pat]\nentropy = 5.5\n");

    assertThat(run(SecretsOptions.NONE, List.of()).findings().items())
        .noneMatch(finding -> "ward:secrets/github-pat".equals(finding.ruleId()));
  }

  @Test
  void theWardGateNeverRunsSecretRulesAndSecretsNeverRunsLanguageRules() throws IOException {
    write(CONFIG, TOKEN_LINE);
    write("src/Long.java", "class Long {\n  int a;\n}\n");
    final AnalysisPorts ports = ports();
    final GateConfiguration ward =
        new GateConfiguration(
            "ward",
            "ward",
            true,
            List.of(),
            Set.of("default"),
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    final GateResult wardResult =
        AnalysisGates.create(ward, ports)
            .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
    final GateResult secretsResult = run(SecretsOptions.NONE, List.of());

    assertThat(wardResult.findings().items())
        .noneMatch(finding -> finding.ruleId().startsWith("ward:secrets/"));
    assertThat(secretsResult.findings().items())
        .allMatch(finding -> finding.ruleId().startsWith("ward:secrets/"));
  }
}
