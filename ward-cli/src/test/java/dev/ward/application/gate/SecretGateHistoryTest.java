package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryPatch.AddedLine;
import dev.ward.domain.secrets.HistoryUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The {@code secrets} gate: SecretGateHistoryTest. */
final class SecretGateHistoryTest extends SecretGateTestSupport {

  @Test
  void theHistoryIsNotReadUnlessAsked() throws IOException {
    write(CONFIG, "clean\n");

    run(SecretsOptions.NONE, List.of());

    assertThat(historyCalls).isEmpty();
  }

  @Test
  void aTokenAddedInACommitIsReportedWithTheCommitAndThePostImageLine() {
    patches =
        List.of(
            new HistoryPatch(
                "abcd1234",
                CONFIG,
                List.of(
                    new AddedLine(HISTORY_LINE - 1, "a: 1"),
                    new AddedLine(HISTORY_LINE, "token: " + TOKEN))));

    final GateResult result =
        run(new SecretsOptions(true, Optional.of(LocalDate.of(2026, 1, 1)), List.of()), List.of());

    final Finding finding = onlyFinding(result);
    assertThat(finding.line()).isEqualTo(HISTORY_LINE);
    assertThat(finding.message()).endsWith("in commit abcd1234");
    assertThat(result.toString()).doesNotContain(TOKEN);
    assertThat(historyCalls).containsExactly("since=2026-01-01");
  }

  @Test
  void anUnreadableHistoryIsAnExecutionError() {
    historyFailure =
        new HistoryUnavailableException("the clone is shallow; fetch the full history");

    final GateResult result = run(new SecretsOptions(true, Optional.empty(), List.of()), List.of());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr()).contains("shallow");
  }

  @Test
  void theHistoryIsLeftOutOfAnEditScan() throws IOException {
    write(CONFIG, "clean\n");

    run(new SecretsOptions(true, Optional.empty(), List.of()), List.of(CONFIG));

    assertThat(historyCalls).isEmpty();
  }
}
