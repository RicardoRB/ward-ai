package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportWriteException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandQualityGateReportDirectoryTest {
  private static final String TRIVY = "trivy";
  private static final String REPORT_PATH = "--report-path";

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private final List<String> prepared = new ArrayList<>();
  private final ReportDirectories recording =
      (root, reportPath) -> prepared.add(root + "|" + reportPath);

  @Test
  void preparesReportPathParentBeforeRunningUnchangedCommand() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final List<String> command = List.of(TRIVY, "detect", REPORT_PATH, "target/trivy.sarif");

    new CommandQualityGate(TRIVY, executor, command, recording).execute(project);

    assertThat(prepared).containsExactly("/p|target/trivy.sarif");
    assertThat(executor.commands).containsExactly(command);
  }

  @Test
  void preparesTheOutputPathParentOfCargoLlvmCov() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final List<String> command =
        List.of("cargo", "llvm-cov", "--lcov", "--output-path", ".ward/reports/coverage.lcov");

    new CommandQualityGate("coverage", executor, command, recording).execute(project);

    assertThat(prepared).containsExactly("/p|.ward/reports/coverage.lcov");
  }

  @Test
  void commandsOfTheOtherToolchainsPrepareNothing() {
    final List<List<String>> commands =
        List.of(
            List.of("./mvnw", "-B", "verify"),
            List.of("./gradlew", "check"),
            List.of("npm", "run", "test", "--", "--coverage"),
            List.of("uv", "run", "pytest", "--cov-report=lcov:.ward/reports/coverage.lcov"),
            List.of("swift", "test", "--enable-code-coverage"),
            List.of("dotnet", "test", "--results-directory", ".ward/reports/dotnet"),
            List.of("cargo", "clippy", "--workspace", "--", "-D", "warnings")
        );

    commands.forEach(
        command ->
            new CommandQualityGate("gate", new FakeCommandExecutor(0, "", ""), command, recording)
                .execute(project)
    );

    assertThat(prepared).isEmpty();
  }

  @Test
  void commandsWithoutReportPathPrepareNothing() {
    new CommandQualityGate(
        TRIVY,
        new FakeCommandExecutor(0, "", ""),
        List.of(TRIVY, "detect", REPORT_PATH),
        recording
    )
        .execute(project);
    new CommandQualityGate(TRIVY, new FakeCommandExecutor(0, "", ""), List.of(TRIVY), recording)
        .execute(project);

    assertThat(prepared).isEmpty();
  }

  @Test
  void preparationFailureStillRunsTheGate() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "leaks found", "");
    final ReportDirectories broken =
        (root, reportPath) -> {
          throw new ReportWriteException("read-only", null);
        };

    final GateResult result =
        new CommandQualityGate(TRIVY, executor, List.of(TRIVY, REPORT_PATH, "target/x"), broken)
            .execute(project);

    assertThat(executor.commands).hasSize(1);
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }
}
