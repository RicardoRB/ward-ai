package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.ProjectFixtures;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.HookPlacement;
import dev.ward.domain.init.QualityHookChange;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedHooksPathTest {
  @TempDir Path root;

  @Test
  void rejectsFoldersOutsideTheRepositoryOrInsideGit() {
    for (final String folder : List.of("", "/abs", "../up", ".git/hooks", "a/../../b")) {
      assertThatThrownBy(() -> HookPlacement.shared(folder, false))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(HookPlacement.shared("team/./hooks/", false).sharedFolder()).isEqualTo("team/hooks");
  }

  @Test
  void setsThePathThroughGitAndRollbackRestoresIt() {
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(1, "", "", FakeCommandExecutor.DURATION),
            new CommandResult(0, "", "", FakeCommandExecutor.DURATION)
        );
    final QualityHookInstaller installer = new QualityHookInstaller(new GitHooksDirectory(git));

    final QualityHookChange change =
        installer.apply(ProjectFixtures.maven(root), HookPlacement.shared(".githooks", true));
    installer.rollback(change);

    assertThat(git.commands)
        .containsSubsequence(
            List.of("git", "config", "--local", "--get", "core.hooksPath"),
            List.of("git", "config", "--local", "core.hooksPath", ".githooks"),
            List.of("git", "config", "--local", "--unset", "core.hooksPath")
        );
    assertThat(root.resolve(".githooks/pre-push")).doesNotExist();
  }

  @Test
  void leavesAnEqualPathAlone() {
    final FakeCommandExecutor git = new FakeCommandExecutor(0, ".githooks\n", "");

    final QualityHookChange change =
        new QualityHookInstaller(new GitHooksDirectory(git))
            .apply(ProjectFixtures.maven(root), HookPlacement.shared(".githooks", true));

    assertThat(change.hooksPath().changed()).isFalse();
    assertThat(git.commands).noneMatch(command -> command.contains("--unset"));
    assertThat(Files.exists(root.resolve(".githooks/pre-commit"))).isTrue();
  }
}
