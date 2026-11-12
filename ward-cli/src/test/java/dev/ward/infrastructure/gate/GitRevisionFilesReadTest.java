package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.RevisionReadException;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class GitRevisionFilesReadTest {
  private static final String SPEC = "api/openapi.yml";
  private final ProcessCommandExecutor executor = new ProcessCommandExecutor();
  private final GitRevisionFiles files = new GitRevisionFiles(executor);

  @TempDir Path root;

  @Test
  void readsTheFileAsCommittedNotAsEdited() throws IOException {
    final String commit = commitSpec("openapi: 3.0.3\n");
    Files.writeString(root.resolve(SPEC), "openapi: 3.1.0\n");

    assertThat(files.read(root, commit, SPEC)).contains("openapi: 3.0.3\n");
  }

  @Test
  void missingFileIsEmpty() throws IOException {
    final String commit = commitSpec("openapi: 3.0.3\n");

    assertThat(files.read(root, commit, "api/other.yml")).isEmpty();
  }

  @Test
  void unreadableExistingFileIsAnError() {
    final FakeCommandExecutor onlyShowFails =
        new FakeCommandExecutor(
            new CommandResult(0, "", "", Duration.ZERO),
            new CommandResult(1, "", "", Duration.ZERO));

    assertThatThrownBy(() -> new GitRevisionFiles(onlyShowFails).read(root, "abc", SPEC))
        .isInstanceOf(RevisionReadException.class);
  }

  private String commitSpec(final String text) throws IOException {
    executor.execute(List.of("git", "init", "-q"), root);
    Files.createDirectories(root.resolve("api"));
    Files.writeString(root.resolve(SPEC), text);
    executor.execute(List.of("git", "add", "--", SPEC), root);
    executor.execute(
        List.of(
            "git",
            "-c",
            "user.name=t",
            "-c",
            "user.email=t@example.test",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-q",
            "-m",
            "x"),
        root);
    return executor.execute(List.of("git", "rev-parse", "HEAD"), root).stdout().trim();
  }
}
