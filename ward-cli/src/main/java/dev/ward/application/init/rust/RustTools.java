package dev.ward.application.init.rust;

import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate choices that run Cargo for a crate or a workspace.
 *
 * @param available whether {@code cargo} is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 */
record RustTools(boolean available, QualityPreset preset) {
  static final String CARGO_CLIPPY = "cargo-clippy";
  static final String CARGO_TEST = "cargo-test";

  private static final String WORKSPACE = "--workspace";
  private static final String ALL_FEATURES = "--all-features";

  /** Returns the Cargo test run over every member; strict also turns every feature on. */
  List<String> testCommand() {
    return withFeatures(List.of("cargo", "test", WORKSPACE));
  }

  /** Returns Clippy over every member and target, where any warning fails the gate. */
  List<String> clippyCommand() {
    return withFeatures(
        List.of("cargo", "clippy", WORKSPACE, "--all-targets"),
        "--",
        "-D",
        "warnings"
    );
  }

  private List<String> withFeatures(final List<String> head, final String... tail) {
    final List<String> command = new ArrayList<>(head);
    if (preset == QualityPreset.STRICT) {
      command.add(ALL_FEATURES);
    }
    command.addAll(List.of(tail));
    return List.copyOf(command);
  }
}
