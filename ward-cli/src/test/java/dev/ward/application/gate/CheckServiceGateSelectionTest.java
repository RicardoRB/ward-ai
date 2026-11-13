package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaceMembers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gate subsets and exclusions of the check service. */
class CheckServiceGateSelectionTest {
  private static final String MYPY = "mypy";
  private static final String PYTEST = "pytest";
  private static final List<String> MYPY_COMMAND = List.of(MYPY, ".");
  private static final List<String> PYTEST_COMMAND = List.of(PYTEST);
  private static final List<String> TESTS_COMMAND = List.of("mvn", "test");

  @TempDir Path dir;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @Test
  void gateSubsetRunsOnlyListedGatesInConfigurationOrder() throws IOException {
    configure(
        """
        version = 2
        [checks.tests]
        command = "mvn test"
        [checks.mypy]
        command = "mypy ."
        [checks.pytest]
        command = "pytest"
        """);

    final CheckReport report = service().check(dir, List.of(), Set.of(PYTEST, MYPY));

    assertThat(report.results()).extracting("name").containsExactly(MYPY, PYTEST);
    assertThat(executor.commands).containsExactly(MYPY_COMMAND, PYTEST_COMMAND);
  }

  @Test
  void gateSubsetWithNothingEnabledIsAnEmptyPassingReport() throws IOException {
    configure(
        """
        version = 2
        [checks.tests]
        command = "mvn test"
        [checks.mypy]
        enabled = false
        command = "mypy ."
        """);

    final CheckReport report = service().check(dir, List.of(), Set.of(MYPY));

    assertThat(report.results()).isEmpty();
    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void gateSubsetHonorsProfileSelection() throws IOException {
    configure(
        """
        version = 2
        [checks.mypy]
        command = "mypy ."
        [checks.pytest]
        command = "pytest"
        profiles = ["ci"]
        """);

    service().check(dir, List.of("ci"), Set.of(MYPY, PYTEST));

    assertThat(executor.commands).containsExactly(PYTEST_COMMAND);
    assertThatThrownBy(() -> service().check(dir, List.of("nope"), Set.of(MYPY)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Unknown profiles");
  }

  @Test
  void excludedGatesDoNotRun() throws IOException {
    configure(
        """
        version = 2
        [checks.tests]
        command = "mvn test"
        [checks.mypy]
        command = "mypy ."
        """);

    final CheckReport report =
        service().check(dir, List.of(), CheckProgressListener.NO_OP, false, Set.of(MYPY, PYTEST));

    assertThat(report.results()).extracting("name").containsExactly("tests");
    assertThat(executor.commands).containsExactly(TESTS_COMMAND);
  }

  @Test
  void excludingEveryEnabledGateIsAnEmptyPass() throws IOException {
    configure("version = 2\n[checks.mypy]\ncommand = \"mypy .\"\n");

    final CheckReport report =
        service().check(dir, List.of(), CheckProgressListener.NO_OP, false, Set.of(MYPY, PYTEST));

    assertThat(report.results()).isEmpty();
    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void excludingNothingKeepsTheNoEnabledGatesError() throws IOException {
    configure("version = 2\n[checks.mypy]\nenabled = false\ncommand = \"mypy .\"\n");

    assertThatThrownBy(
            () ->
                service()
                    .check(
                        dir, List.of(), CheckProgressListener.NO_OP, false, Set.of(MYPY, PYTEST)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("No quality gates are enabled");
  }

  private void configure(final String toml) throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), toml);
  }

  private CheckService service() {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner(),
        new PartialPorts(new FilePathExistence(), new NodeWorkspaceMembers()));
  }
}
