package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckServiceParallelTest {
  private static final int JOBS = 4;
  private static final String CONFIGURATION = """
      version = 2
      [checks.ward]
      uses = "ward"
      rules = ["ward:java/type-name"]
      [checks.tests]
      uses = "tests"
      command = "build test"
      [checks.sbom]
      uses = "command"
      command = "build sbom"
      [checks.drift]
      uses = "command"
      command = "build drift"
      """;

  /** A project whose one class is named {@code type}; a lower-case name fails the ward check. */
  private static Path project(final Path dir, final String type) throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), CONFIGURATION);
    Files.createDirectories(dir.resolve("src/main/java"));
    Files.writeString(dir.resolve("src/main/java/" + type + ".java"), "class " + type + " {\n}\n");
    return dir;
  }

  /** The second command fails, the others pass. */
  private static FakeCommandExecutor executor() {
    return new FakeCommandExecutor(
        new CommandResult(0, "", "", FakeCommandExecutor.DURATION),
        new CommandResult(1, "", "stale", FakeCommandExecutor.DURATION),
        new CommandResult(0, "", "", FakeCommandExecutor.DURATION)
    );
  }

  private static CheckReport check(
      final Path dir,
      final FakeCommandExecutor executor,
      final boolean failFast,
      final int jobs
  ) {
    return WardAnalysisFixtures.checks(executor)
        .checkWith(
            dir,
            new CheckRunOptions(List.of(), CheckProgressListener.NO_OP, failFast, Set.of(), jobs)
        );
  }

  /** The results without the time the in-process gate took, which differs between runs. */
  private static List<String> comparable(final CheckReport report) {
    return report.results().stream()
        .map(
            result ->
                String.join(
                    "|",
                    result.name(),
                    result.status().name(),
                    String.valueOf(result.exitCode()),
                    String.valueOf(result.summary()),
                    result.findings().toString()
                )
        )
        .toList();
  }

  @Test
  void commandChecksRunInConfigurationOrderWithManyJobs(@TempDir final Path dir)
      throws IOException {
    final FakeCommandExecutor executor = executor();

    final CheckReport report = check(project(dir, "order"), executor, false, JOBS);

    assertThat(executor.commands)
        .containsExactly(
            List.of("build", "test"),
            List.of("build", "sbom"),
            List.of("build", "drift")
        );
    assertThat(report.results())
        .extracting(GateResult::name)
        .containsExactly("ward", "tests", "sbom", "drift");
  }

  @Test
  void aFailingCheckGivesTheSameReportForOneAndManyJobs(@TempDir final Path dir)
      throws IOException {
    final CheckReport serial = check(project(dir, "order"), executor(), false, 1);
    final CheckReport parallel = check(dir, executor(), false, JOBS);

    assertThat(comparable(parallel)).isEqualTo(comparable(serial));
    assertThat(parallel.results())
        .extracting(GateResult::status)
        .containsExactly(
            GateStatus.FAILED,
            GateStatus.PASSED,
            GateStatus.FAILED,
            GateStatus.PASSED
        );
    assertThat(parallel.results().getFirst().findings().items()).hasSize(1);
  }

  @Test
  void failFastGivesTheSameReportAndStartsNoLaterCommand(@TempDir final Path dir)
      throws IOException {
    final FakeCommandExecutor executor = executor();
    final CheckReport serial = check(project(dir, "Order"), executor(), true, 1);

    final CheckReport parallel = check(dir, executor, true, JOBS);

    assertThat(comparable(parallel)).isEqualTo(comparable(serial));
    assertThat(executor.commands)
        .containsExactly(List.of("build", "test"), List.of("build", "sbom"));
  }

  @Test
  void onlyChecksThatReadSourcesAreIndependent(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("ward.toml"), """
        version = 2
        [checks.ward]
        uses = "ward"
        [checks.typed]
        uses = "ward"
        java-classpath = "target/classpath.txt"
        [checks.tests]
        uses = "tests"
        command = "build test"
        [checks.off]
        uses = "command"
        enabled = false
        command = "build off"
        """);
    final WardConfiguration configuration =
        new TomlConfigurationReader().read(dir.resolve("ward.toml"));

    final List<QualityGate> gates =
        WardAnalysisFixtures.factory(executor()).create(configuration);

    assertThat(gates)
        .extracting(QualityGate::independent)
        .containsExactly(true, false, false, true);
  }
}
