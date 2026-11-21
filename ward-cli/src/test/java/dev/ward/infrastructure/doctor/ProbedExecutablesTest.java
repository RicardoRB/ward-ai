package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Init and doctor only see an executable that the inspection probes for. */
class ProbedExecutablesTest {
  @Test
  void theDotnetAndCargoToolchainsAreProbed() {
    assertThat(SystemEnvironmentInspection.PROBED_EXECUTABLES)
        .contains("dotnet", "cargo", "cargo-llvm-cov", "cargo-mutants");
  }
}
