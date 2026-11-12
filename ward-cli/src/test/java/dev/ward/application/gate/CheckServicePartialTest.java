package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateScope;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Partial checks: which gates run after an edit, with which argument vector. */
class CheckServicePartialTest {
  private static final String CONFIGURATION =
      """
      version = 2
      [checks.format]
      profiles = ["default", "edit"]
      command = "mvn spotless:check"
      [checks.lint]
      profiles = ["default", "edit"]
      command = "mvn lint:check"
      [checks.lint.paths]
      match = ["src/**/*.java"]
      full = ["config/lint.xml", "pom.xml"]
      args = ["--lint-includes={paths}"]
      item = "**/{name}"
      separator = ","
      [checks.style]
      profiles = ["default", "edit"]
      command = "mvn style:check"
      [checks.style.paths]
      match = ["src/**/*.java"]
      full = ["pom.xml"]
      [checks.tests]
      uses = "tests"
      command = "mvn test"
      profiles = ["pre-push"]
      [checks.tests.paths]
      match = ["src/**/*.java"]
      """;
  private static final List<String> FORMAT = List.of("mvn", "spotless:check");
  private static final List<String> CHECKSTYLE = List.of("mvn", "lint:check");
  private static final List<String> PMD = List.of("mvn", "style:check");
  private static final String ORDER = "src/main/java/com/acme/Order.java";

  @TempDir Path dir;

  private FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @BeforeEach
  void project() throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), CONFIGURATION);
    Files.createDirectories(dir.resolve(ORDER).getParent());
    Files.writeString(dir.resolve(ORDER), "class Order {}\n");
  }

  @Test
  void irrelevantEditSkipsPartialGatesWithoutSpawningThem() {
    final CheckReport report = partial("README.md");

    assertThat(executor.commands).containsExactly(FORMAT);
    assertThat(report.results())
        .extracting(GateResult::name, GateResult::status, GateResult::scope)
        .containsExactly(
            tuple("format", GateStatus.PASSED, GateScope.FULL),
            tuple("lint", GateStatus.SKIPPED, GateScope.SKIPPED),
            tuple("style", GateStatus.SKIPPED, GateScope.SKIPPED));
    assertThat(report.results().get(1).summary()).isEqualTo(PartialGates.NOT_AFFECTED);
    assertThat(report.partialPaths()).containsExactly("README.md");
  }

  @Test
  void sourceEditNarrowsGatesThatDeclareArgumentsAndRunsTheRestInFull() {
    final CheckReport report = partial(ORDER);

    assertThat(executor.commands)
        .containsExactly(
            FORMAT, List.of("mvn", "lint:check", "--lint-includes=**/Order.java"), PMD);
    assertThat(report.results())
        .extracting(GateResult::scope)
        .containsExactly(GateScope.FULL, GateScope.NARROWED, GateScope.FULL);
  }

  @Test
  void configurationEditRunsTheGateWithItsUnchangedCommand() {
    partial("pom.xml");

    assertThat(executor.commands).containsExactly(FORMAT, CHECKSTYLE, PMD);
  }

  @Test
  void deletedSourceRunsTheGateInFull() {
    partial("src/main/java/com/acme/Deleted.java");

    assertThat(executor.commands).containsExactly(FORMAT, CHECKSTYLE, PMD);
  }

  @Test
  void skippedGatesDoNotDecideTheVerdict() {
    executor = new FakeCommandExecutor(1, "", "");

    final CheckReport failing = partial("README.md");

    assertThat(failing.passed()).isFalse();
    executor = new FakeCommandExecutor(0, "", "");
    assertThat(partial("README.md").passed()).isTrue();
  }

  @Test
  void namedProfileSelectsItsOwnGates() {
    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(
                dir,
                List.of(dir.resolve(ORDER)),
                new CheckRunOptions(
                    List.of("pre-push"), CheckProgressListener.NO_OP, false, Set.of()));

    assertThat(report.results()).extracting(GateResult::name).containsExactly("tests");
    assertThat(report.results().getFirst().scope()).isEqualTo(GateScope.FULL);
  }

  @Test
  void progressAndFailFastApplyToPartialChecks() {
    executor = new FakeCommandExecutor(1, "", "");
    final List<String> finished = new ArrayList<>();

    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(
                dir,
                List.of(dir.resolve(ORDER)),
                new CheckRunOptions(
                    List.of(),
                    new CheckProgressListener() {
                      @Override
                      public void gateFinished(final GateResult result) {
                        finished.add(result.name());
                      }
                    },
                    true,
                    Set.of()));

    assertThat(executor.commands).containsExactly(FORMAT);
    assertThat(finished).containsExactly("format", "lint", "style");
    assertThat(report.results().get(1).status()).isEqualTo(GateStatus.SKIPPED);
  }

  @Test
  void excludingEveryRemainingGateReturnsAnEmptyReport() throws IOException {
    Files.writeString(
        dir.resolve("ward.toml"),
        """
        version = 2
        [checks.mypy]
        profiles = ["default", "edit"]
        command = "mypy ."
        """);

    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(dir, List.of(dir.resolve(ORDER)), Set.of("mypy"));

    assertThat(report.results()).isEmpty();
    assertThat(executor.commands).isEmpty();
  }

  private CheckReport partial(final String path) {
    return CheckServiceFixtures.checks(executor)
        .checkPartial(dir, List.of(dir.resolve(path)), Set.of());
  }
}
