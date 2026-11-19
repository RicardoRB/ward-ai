package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.rust.RustInitFixtures;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import dev.ward.infrastructure.analysis.rust.RustDependencyAdapters;
import dev.ward.infrastructure.finding.LcovLineCoverage;
import dev.ward.infrastructure.finding.rust.CargoMutantsReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The scenarios of story R7: coverage, mutation and architecture gates of a Rust project. */
class RustGateScenariosTest {
  private static final int COVERAGE_MINIMUM = 80;
  private static final int MUTATION_MINIMUM = 60;
  private static final int MISSED_EXIT = 2;
  private static final String MISSED = "src/lib.rs:1:30: replace one -> u8 with 0";

  @TempDir Path root;

  @Test
  @DisplayName("an untested file fails coverage")
  void anUntestedFileFailsCoverage() throws IOException {
    // Given an initialized Rust project at the coverage minimum
    final GateConfiguration coverage =
        RustInitFixtures.initialize(root, QualityPreset.STANDARD).checks().get("coverage");
    assertThat(coverage.command())
        .containsExactly(
            "cargo",
            "llvm-cov",
            "--workspace",
            "--lcov",
            "--output-path",
            ".ward/reports/coverage.lcov"
        );
    assertThat(coverage.minimum()).isEqualTo(COVERAGE_MINIMUM);
    final CoverageGateFixture gate = new CoverageGateFixture();
    gate.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        false,
        lcov(10, 10, "src/lib.rs")
    );
    assertThat(gate.run(COVERAGE_MINIMUM, null, null).status()).isEqualTo(GateStatus.PASSED);

    // When an untested source file is added
    final CoverageGateFixture withUntested = new CoverageGateFixture();
    withUntested.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        false,
        lcov(10, 10, "src/lib.rs"),
        lcov(10, 0, "src/untested.rs")
    );
    final GateResult result = withUntested.run(coverage.minimum(), null, null);

    // Then the coverage gate fails and states the line coverage
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("Line coverage 50% is below the minimum of 80%.");
  }

  @Test
  @DisplayName("a surviving mutant")
  void aSurvivingMutant() throws IOException {
    // Given a test that does not assert a result
    final GateConfiguration mutants =
        RustInitFixtures.initialize(root, QualityPreset.STRICT).checks().get("cargo-mutants");
    assertThat(mutants.command()).containsExactly("cargo", "mutants", "--workspace");
    assertThat(mutants.minimum()).isEqualTo(MUTATION_MINIMUM);
    final CommandExecutor cargo =
        (command, directory) -> {
          writeOutcomes(directory);
          return new CommandResult(MISSED_EXIT, "", "", Duration.ZERO);
        };
    final QualityGateFactory factory =
        new QualityGateFactory(
            cargo,
            new GateReportPorts(
                ReportDirectories.NONE,
                null,
                null,
                null,
                PlatformGatePorts.NONE,
                new CargoMutantsReport()
            ),
            null
        );

    // When the mutation gate runs
    final GateResult result =
        factory
            .create(new WardConfiguration(1, Map.of("cargo-mutants", mutants)))
            .getFirst()
            .execute(new Project(root, Language.RUST, BuildTool.CARGO, Framework.NONE));

    // Then it fails and names the surviving mutant
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("Mutation score 0%", MISSED);
  }

  @Test
  @DisplayName("a layer violation")
  void aLayerViolation() throws IOException {
    // Given a Rust module in the domain layer that uses an infrastructure module
    write("Cargo.toml", "[package]\nname = \"shop\"\nversion = \"0.1.0\"\n");
    write("src/lib.rs", "pub mod domain;\npub mod infrastructure;\n");
    write(
        "src/domain/mod.rs",
        "use crate::infrastructure::db::connect;\n\npub fn run() { connect(); }\n"
    );
    write("src/infrastructure/mod.rs", "pub mod db;\n");
    write("src/infrastructure/db.rs", "pub fn connect() {}\n");

    // When the architecture gate runs
    final List<Finding> findings =
        LayerPorts.findings(
            new Project(root, Language.RUST, BuildTool.CARGO, Framework.NONE),
            new LayerBoundary(List.of("domain"), List.of("infrastructure")),
            new ProjectDependencyAdapters(List.of(new RustDependencyAdapters()))
        );

    // Then it reports the dependency
    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().file()).isEqualTo("src/domain/mod.rs");
    assertThat(findings.getFirst().message())
        .isEqualTo("Layer domain must not depend on infrastructure: src/infrastructure/db.rs");
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private static void writeOutcomes(final Path directory) {
    try {
      final Path outputs = directory.resolve("mutants.out");
      Files.createDirectories(outputs);
      Files.writeString(outputs.resolve("caught.txt"), "");
      Files.writeString(outputs.resolve("timeout.txt"), "");
      Files.writeString(outputs.resolve("unviable.txt"), "");
      Files.writeString(outputs.resolve("missed.txt"), MISSED + "\n");
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** The entry the lcov reader makes of a file with {@code lines} lines, {@code hit} of them hit. */
  private LineHits.Entry lcov(final int lines, final int hit, final String name)
      throws IOException {
    final StringBuilder text = new StringBuilder("TN:\nSF:" + name + "\n");
    for (int line = 1; line <= lines; line++) {
      text.append("DA:").append(line).append(',').append(line <= hit ? 1 : 0).append('\n');
    }
    text.append("end_of_record\n");
    final Path report = root.resolve(name.replace('/', '_') + ".lcov");
    Files.writeString(report, text.toString());
    return new LcovLineCoverage().read(report).entries().getFirst();
  }
}
