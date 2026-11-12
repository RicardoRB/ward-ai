package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class ReleaseTagInspectionTest {
  private final ProcessCommandExecutor executor = new ProcessCommandExecutor();
  private final SystemEnvironmentInspection inspection = new SystemEnvironmentInspection(executor);

  @TempDir Path root;

  @Test
  void seesAReleaseTagButNotAPreReleaseOrNoRepository() {
    assertThat(inspection.hasReleaseTag(root)).isFalse();
    git("init", "-q");
    git("commit", "-q", "--allow-empty", "-m", "first");
    git("tag", "v1.0.0-rc1");
    assertThat(inspection.hasReleaseTag(root)).isFalse();
    git("tag", "v1.0.0");
    assertThat(inspection.hasReleaseTag(root)).isTrue();
  }

  private void git(final String... arguments) {
    final List<String> command =
        new java.util.ArrayList<>(
            List.of(
                "git",
                "-c",
                "user.name=Test",
                "-c",
                "user.email=test@example.com",
                "-c",
                "commit.gpgsign=false",
                "-c",
                "tag.gpgsign=false"));
    command.addAll(List.of(arguments));
    executor.execute(command, root);
  }
}
