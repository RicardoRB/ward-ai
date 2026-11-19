package dev.ward.application.init.rust;

import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gate choices that run Cargo for a crate or a workspace.
 *
 * @param available whether {@code cargo} is on the path
 * @param coverageAvailable whether {@code cargo-llvm-cov} is on the path
 * @param mutantsAvailable whether {@code cargo-mutants} is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 */
record RustTools(
    boolean available,
    boolean coverageAvailable,
    boolean mutantsAvailable,
    QualityPreset preset
) {
  static final String CARGO_CLIPPY = "cargo-clippy";
  static final String CARGO_TEST = "cargo-test";
  static final String COVERAGE = "coverage";
  static final String CARGO_MUTANTS = "cargo-mutants";
  /** The lcov report that {@code cargo llvm-cov} writes and Ward reads. */
  static final String REPORT = ".ward/reports/coverage.lcov";
  static final String LLVM_COV_EXECUTABLE = "cargo-llvm-cov";
  static final String MUTANTS_EXECUTABLE = "cargo-mutants";

  private static final int STANDARD_COVERAGE = 80;
  private static final int STRICT_COVERAGE = 85;
  private static final int STRICT_MUTATION = 60;

  private static final String WORKSPACE = "--workspace";
  private static final String ALL_FEATURES = "--all-features";

  /** Returns the Cargo test run over every member; strict also turns every feature on. */
  List<String> testCommand() {
    return withFeatures(List.of("cargo", "test", WORKSPACE));
  }

  /** Returns the test run under cargo-llvm-cov, which writes the lcov report Ward checks. */
  List<String> coverageCommand() {
    return withFeatures(
        List.of("cargo", "llvm-cov", WORKSPACE),
        "--lcov",
        "--output-path",
        REPORT
    );
  }

  /** Returns cargo-mutants over every member; it writes its results under {@code mutants.out}. */
  List<String> mutantsCommand() {
    return List.of("cargo", "mutants", WORKSPACE);
  }

  /** Returns the line coverage minimum of the preset, in percent. */
  String coverageMinimum() {
    return String.valueOf(preset == QualityPreset.STRICT ? STRICT_COVERAGE : STANDARD_COVERAGE);
  }

  /** Returns the mutation score minimum of strict, or nothing for another preset. */
  Optional<String> mutationMinimum() {
    return preset == QualityPreset.STRICT
        ? Optional.of(String.valueOf(STRICT_MUTATION))
        : Optional.empty();
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
