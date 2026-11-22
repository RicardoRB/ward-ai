package dev.ward.application.gate.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.CommandQualityGate;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Exit codes 2 and 3 of cargo-mutants are results, not failures to run. */
class MissedMutantsToleratedTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo"), Language.RUST, BuildTool.CARGO, Framework.NONE);

  private static GateResult run(final int exitCode) {
    return new MissedMutantsTolerated(
        new CommandQualityGate(
            "cargo-mutants",
            new FakeCommandExecutor(exitCode, "out", ""),
            List.of("cargo", "mutants")
        )
    )
        .execute(PROJECT);
  }

  @ParameterizedTest
  @CsvSource({"0,PASSED", "2,PASSED", "3,PASSED", "1,FAILED", "4,FAILED"})
  void onlyTheMutantResultExitCodesAreTolerated(final int exitCode, final GateStatus status) {
    assertThat(run(exitCode).status()).isEqualTo(status);
  }

  @Test
  void theResultKeepsTheExitCodeAndTheOutput() {
    final GateResult result = run(2);

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stdout()).isEqualTo("out");
    assertThat(result.name()).isEqualTo("cargo-mutants");
  }
}
