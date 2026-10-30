package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.TaskRunner;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every task runner that runs package tasks has a strategy; a runner without one has none. */
class TaskRunnerStrategiesTest {
  @Test
  void turboAndItsMembersShareTheTurboStrategy() {
    assertThat(TaskRunnerStrategies.of(TaskRunner.TURBO)).isPresent();
    assertThat(TaskRunnerStrategies.of(TaskRunner.TURBO_MEMBER)).isPresent();
  }

  @Test
  void aProjectWithoutRunnerHasNoStrategy() {
    assertThat(TaskRunnerStrategies.of(TaskRunner.NONE)).isEmpty();
  }

  @Test
  void turboStrategyRunsTheTaskThroughThePackageManager() {
    assertThat(
            TaskRunnerStrategies.of(TaskRunner.TURBO)
                .orElseThrow()
                .rootTaskCommand(BuildTool.PNPM, "lint"))
        .containsExactly(
            "pnpm",
            "exec",
            "turbo",
            "run",
            "lint",
            "--log-prefix=task",
            "--log-order=grouped",
            "--continue=always",
            "--ui=stream");
    assertThat(TaskRunnerStrategies.of(TaskRunner.TURBO).orElseThrow().packageFilter())
        .isEqualTo("--filter=...{package}");
  }

  @Test
  void runnersDescribeThemselves() {
    assertThat(TaskRunner.TURBO.displayName()).contains("Turborepo");
    assertThat(TaskRunner.TURBO.packageName()).contains("turbo");
    assertThat(TaskRunner.TURBO.gitignoreEntry()).isEqualTo(".turbo/\n");
    assertThat(TaskRunner.NONE.gitignoreEntry()).isEmpty();
    assertThat(TaskRunner.TURBO.isRootRunner()).isTrue();
    assertThat(TaskRunner.TURBO_MEMBER.isRootRunner()).isFalse();
    assertThat(TaskRunner.NONE.displayName()).isEmpty();
    assertThat(List.of(TaskRunner.values())).contains(TaskRunner.NONE);
  }
}
