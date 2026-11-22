package dev.ward.application.init.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The coverage and mutation gates that Rust init offers, by preset and by installed tools. */
class RustGatesTest {
  private final RustGates gates = new RustGates();

  private InitGateOption option(final RustTools tools, final String id) {
    return gates.analysisGates(tools).stream()
        .filter(gate -> id.equals(gate.id()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void strictAddsMutationTestingAndFeaturesToTheCoverageRun() {
    final RustTools strict = new RustTools(true, true, true, QualityPreset.STRICT);

    assertThat(gates.presetGates(QualityPreset.STANDARD))
        .containsExactly("cargo-clippy", "cargo-test", "coverage");
    assertThat(gates.presetGates(QualityPreset.STRICT)).contains("cargo-mutants");
    assertThat(option(strict, "coverage").command())
        .containsExactly(
            "cargo",
            "llvm-cov",
            "--workspace",
            "--all-features",
            "--lcov",
            "--output-path",
            ".ward/reports/coverage.lcov"
        );
    assertThat(option(strict, "coverage").settings()).containsEntry("minimum", "85");
    assertThat(option(strict, "cargo-mutants").settings()).containsEntry("minimum", "60");
  }

  @Test
  void standardRunsMutantsWithoutAMinimum() {
    final RustTools standard = new RustTools(true, true, true, QualityPreset.STANDARD);

    assertThat(option(standard, "cargo-mutants").settings()).isEmpty();
    assertThat(option(standard, "coverage").settings())
        .containsEntry("minimum", "80")
        .containsEntry("report", ".ward/reports/coverage.lcov");
  }

  @Test
  void aMissingToolIsUnavailableAndNamesTheInstallCommand() {
    final RustTools none = new RustTools(true, false, false, QualityPreset.STRICT);

    assertThat(option(none, "coverage").available()).isFalse();
    assertThat(option(none, "coverage").availabilityMessage())
        .contains("cargo install --locked cargo-llvm-cov");
    assertThat(option(none, "cargo-mutants").availabilityMessage())
        .contains("cargo install --locked cargo-mutants");
    assertThat(List.of(option(none, "cargo-test")).getFirst().available()).isTrue();
  }
}
