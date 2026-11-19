package dev.ward.application.init.rust;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Map;

/**
 * Rust's gates on Cargo, all over the whole workspace: Clippy, the test run, coverage with
 * cargo-llvm-cov and, in strict, mutation testing with cargo-mutants. Rust's lint rules come from
 * the {@code ward} gate and {@code ward-rules.toml}. Strict adds {@code --all-features}.
 */
public final class RustGates implements SourceLanguageGates<RustTools> {
  private static final List<String> STANDARD =
      List.of(RustTools.CARGO_CLIPPY, RustTools.CARGO_TEST, RustTools.COVERAGE);
  private static final List<String> STRICT =
      List.of(
          RustTools.CARGO_CLIPPY,
          RustTools.CARGO_TEST,
          RustTools.COVERAGE,
          RustTools.CARGO_MUTANTS
      );

  @Override
  public List<InitGateOption> analysisGates(final RustTools rust) {
    return List.of(
        option(
            rust,
            RustTools.CARGO_CLIPPY,
            "Runs Clippy on every member crate and target; any warning fails.",
            rust.clippyCommand()
        ),
        option(
            rust,
            RustTools.CARGO_TEST,
            "Runs the tests of every member crate.",
            rust.testCommand()
        ),
        coverage(rust),
        mutants(rust)
    );
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }

  /** The minimum goes in {@code ward.toml}; Ward reads the lcov report and decides. */
  private static InitGateOption coverage(final RustTools rust) {
    return new InitGateOption(
        RustTools.COVERAGE,
        "Runs the tests under cargo-llvm-cov and checks the lcov report against the minimum.",
        rust.coverageCommand(),
        rust.coverageAvailable(),
        rust.coverageAvailable()
            ? "cargo-llvm-cov available."
            : "cargo-llvm-cov is not on PATH; run `cargo install --locked cargo-llvm-cov`.",
        Map.of("minimum", rust.coverageMinimum(), "report", RustTools.REPORT)
    );
  }

  /** Strict sets the mutation minimum; another preset runs cargo-mutants with none. */
  private static InitGateOption mutants(final RustTools rust) {
    return new InitGateOption(
        RustTools.CARGO_MUTANTS,
        "Runs mutation testing with cargo-mutants; strict fails below a 60% mutation score.",
        rust.mutantsCommand(),
        rust.mutantsAvailable(),
        rust.mutantsAvailable()
            ? "cargo-mutants available."
            : "cargo-mutants is not on PATH; run `cargo install --locked cargo-mutants`.",
        rust.mutationMinimum().map(minimum -> Map.of("minimum", minimum)).orElse(Map.of())
    );
  }

  private static InitGateOption option(
      final RustTools rust,
      final String id,
      final String description,
      final List<String> command
  ) {
    return new InitGateOption(
        id,
        description,
        command,
        rust.available(),
        rust.available()
            ? "cargo available."
            : "cargo is not on PATH; install Rust with rustup (https://rustup.rs)."
    );
  }
}
