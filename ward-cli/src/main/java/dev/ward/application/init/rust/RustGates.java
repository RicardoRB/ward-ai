package dev.ward.application.init.rust;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * Rust's gates on Cargo: Clippy and the test run, both over the whole workspace. Rust's lint rules
 * come from the {@code ward} gate and {@code ward-rules.toml}. Strict adds {@code --all-features}.
 */
public final class RustGates implements SourceLanguageGates<RustTools> {
  private static final List<String> PRESET = List.of(RustTools.CARGO_CLIPPY, RustTools.CARGO_TEST);

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
        )
    );
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return PRESET;
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
