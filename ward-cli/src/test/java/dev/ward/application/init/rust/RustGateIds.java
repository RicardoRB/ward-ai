package dev.ward.application.init.rust;

import dev.ward.domain.init.QualityPreset;
import java.util.List;

/** The Rust gate ids a preset selects, for tests. */
final class RustGateIds {
  private RustGateIds() {}

  static List<String> forPreset(final QualityPreset preset) {
    return preset == QualityPreset.STRICT
        ? List.of("cargo-clippy", "cargo-test", "coverage", "cargo-mutants")
        : List.of("cargo-clippy", "cargo-test", "coverage");
  }
}
