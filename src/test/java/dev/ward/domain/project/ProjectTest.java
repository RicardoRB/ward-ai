package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
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
}
