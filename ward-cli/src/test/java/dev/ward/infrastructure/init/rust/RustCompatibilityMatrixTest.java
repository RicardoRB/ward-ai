package dev.ward.infrastructure.init.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.init.MatrixFileTrees;
import dev.ward.infrastructure.init.rust.RustMatrixProcesses.Run;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs each Rust matrix cell through the real CLI: {@code init}, the clean {@code check}, then an
 * untested module, which must make {@code coverage} fail, then the seeded violations, which must
 * make every selected gate except {@code coverage} and {@code cargo-mutants} fail. Each cell runs
 * under its toolchain, selected for the Ward process only through {@code RUSTUP_TOOLCHAIN}, and
 * with the edition written into the fixture's {@code Cargo.toml}. Runs only for the cells whose
 * toolchain is named by {@code ward.rust.matrix} ({@code stable} or a version such as {@code
 * 1.85}), with the Ward jar named by {@code ward.rust.matrix.jar}. It needs {@code rustup} with
 * that toolchain installed, {@code cargo-llvm-cov} and {@code cargo-mutants}.
 */
@EnabledIfSystemProperty(named = RustCompatibilityMatrixTest.TOOLCHAIN_PROPERTY, matches = "\\S+")
class RustCompatibilityMatrixTest {
  static final String TOOLCHAIN_PROPERTY = "ward.rust.matrix";
  private static final String JAR_PROPERTY = "ward.rust.matrix.jar";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/rust-matrix");
  private static final Path VIOLATIONS = Path.of("src/test/resources/rust-matrix/violations");
  private static final Path COVERAGE = Path.of("src/test/resources/rust-matrix/coverage");

  /** Coverage has its own run and mutation testing is clean-only. */
  private static final Set<String> NOT_VIOLATED = Set.of("coverage", "cargo-mutants");

  private static final String PASSED = "PASSED";
  private static final String FAILED = "FAILED";

  @ParameterizedTest(name = "rust {0} edition {1} {2} {3}")
  @CsvFileSource(resources = "/rust-matrix/cells.csv", numLinesToSkip = 1)
  void cellInitsChecksAndCatchesEveryViolation(
      final ArgumentsAccessor cell,
      @TempDir final Path project
  ) throws IOException, InterruptedException {
    final String toolchain = cell.getString(0);
    final String fixture = cell.getString(3);
    Assumptions.assumeTrue(
        toolchain.equals(System.getProperty(TOOLCHAIN_PROPERTY)),
        "row is for toolchain " + toolchain
    );
    final Map<String, String> env = Map.of("RUSTUP_TOOLCHAIN", toolchain);
    assertToolchainIsInstalled(env, toolchain);
    MatrixFileTrees.copy(FIXTURES.resolve(fixture), project);
    final Path manifest = project.resolve("Cargo.toml");
    Files.writeString(manifest, Files.readString(manifest).replace("@EDITION@", cell.getString(1)));

    final Run init =
        ward(env, project, "init", "--preset", cell.getString(2), "--integration", "none", "--yes");
    assertThat(init.exit()).as("init output:\n%s", init.output()).isZero();
    final Map<String, String> clean = report(env, project);
    assertThat(clean).as("clean check").isNotEmpty();
    assertThat(clean.entrySet().stream().filter(gate -> !PASSED.equals(gate.getValue())).toList())
        .as("gates that fail on the clean project")
        .isEmpty();

    assertUntestedModuleFailsCoverage(env, project, fixture);
    MatrixFileTrees.copy(VIOLATIONS.resolve(fixture), project);
    final Map<String, String> violated = report(env, project);
    clean.keySet().stream()
        .filter(gate -> !NOT_VIOLATED.contains(gate))
        .forEach(
            gate ->
                assertThat(violated.get(gate))
                    .as("seeded violation for gate %s", gate)
                    .isEqualTo(FAILED)
        );
  }

  private static void assertToolchainIsInstalled(
      final Map<String,
      String> env,
      final String toolchain
  ) throws IOException, InterruptedException {
    final Run version = RustMatrixProcesses.exec(env, Path.of("."), List.of("rustc", "--version"));
    assertThat(version.exit()).as("rustc --version\n%s", version.output()).isZero();
    if (!"stable".equals(toolchain)) {
      assertThat(version.output()).as("the toolchain of the cell").startsWith("rustc " + toolchain);
    }
  }

  private static void assertUntestedModuleFailsCoverage(
      final Map<String,
      String> env,
      final Path project,
      final String fixture
  ) throws IOException, InterruptedException {
    final Path untested = COVERAGE.resolve(fixture);
    MatrixFileTrees.copy(untested, project);
    final Run run = ward(env, project, "check", "--profile", "ci", "--format", "json");
    assertThat(RustMatrixProcesses.checks(run).get("coverage"))
        .as("coverage with an untested module\n%s", run.output())
        .isEqualTo(FAILED);
    assertThat(run.output()).as("coverage finding").contains("Line coverage").contains("is below");
    MatrixFileTrees.restore(untested, FIXTURES.resolve(fixture), project);
  }

  private static Map<String, String> report(final Map<String, String> env, final Path project)
      throws IOException, InterruptedException {
    return RustMatrixProcesses.checks(
        ward(env, project, "check", "--profile", "ci", "--format", "json")
    );
  }

  private static Run ward(final Map<String, String> env, final Path project, final String... args)
      throws IOException, InterruptedException {
    return RustMatrixProcesses.ward(System.getProperty(JAR_PROPERTY), env, project, args);
  }
}
