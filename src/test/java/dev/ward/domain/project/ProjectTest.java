package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectTest {
  @ParameterizedTest
  @CsvSource({"NPM, npm", "PNPM, pnpm", "YARN, yarn", "BUN, bun"})
  void nodeManagersRunTheirOwnExecutable(final BuildTool tool, final String executable) {
    final Project project = new Project(Path.of("app"), Language.TYPESCRIPT, tool, Framework.NONE);

    assertThat(project.buildExecutable()).isEqualTo(executable);
    assertThat(project.languageVersion()).isEmpty();
    assertThat(project.withRepositoryRoot(Path.of(".")).languageVersion()).isEmpty();
  }

  @Test
  void taskRunnerDefaultsToNoneAndIsCopiedWithRepositoryRoot() {
    final Project plain =
        new Project(Path.of("app"), Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE);
    final Project turbo =
        new Project(
            Path.of("app"),
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            Path.of("app"),
            Set.of(),
            "",
            TaskRunner.TURBO);

    assertThat(plain.taskRunner()).isEqualTo(TaskRunner.NONE);
    assertThat(turbo.withRepositoryRoot(Path.of(".")).taskRunner()).isEqualTo(TaskRunner.TURBO);
  }
}
