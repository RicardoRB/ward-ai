package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.RevisionReadException;
import dev.ward.domain.gate.RevisionSnapshot;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class GitRevisionSnapshotTest {
  private static final int FILES = 25;
  private static final int BOUND = 3;
  private static final String BLOB_ID = "0123456789abcdef0123456789abcdef01234567";
  private static final String LISTING = "100644 blob " + BLOB_ID + "\tA.java\0";
  private static final long SECONDS = 5;

  @TempDir Path repository;
  private final List<List<String>> started = new ArrayList<>();
  private final List<Process> processes = new ArrayList<>();

  @Test
  @DisplayName("one git process per revision")
  void oneGitProcessPerRevision() throws IOException {
    // Given
    final ProcessCommandExecutor real = new ProcessCommandExecutor();
    final List<List<String>> executed = new ArrayList<>();
    final CommandExecutor counting =
        (command, directory) -> {
          executed.add(command);
          return real.execute(command, directory);
        };
    git(real, "init", "-q");
    for (int i = 0; i < FILES; i++) {
      Files.writeString(repository.resolve("F" + i + ".java"), "class F" + i + " {}");
    }
    git(real, "add", "-A");
    git(real, "commit", "-q", "-m", "x");
    final String commit = real.execute(
        List.of("git", "rev-parse", "HEAD"),
        repository
    ).stdout().trim();
    final GitRevisionFiles files = new GitRevisionFiles(counting, this::start);

    // When
    final List<String> texts = new ArrayList<>();
    try (RevisionSnapshot snapshot = files.open(repository, commit)) {
      for (final String name : snapshot.files("")) {
        texts.add(snapshot.read(name).orElseThrow());
      }
      assertThat(snapshot.read("absent.java")).isEmpty();
    }

    // Then
    assertThat(texts).hasSize(FILES).contains("class F7 {}");
    assertThat(executed.size() + started.size()).isLessThanOrEqualTo(BOUND);
    assertThat(started).hasSize(1);
    assertThat(started.getFirst()).contains("cat-file", "--batch");
  }

  @Test
  @DisplayName("a missing blob is reported as an unreadable file")
  void missingBlobIsReported() throws IOException {
    // Given: a batch process that answers "<id> missing"
    final GitRevisionFiles files = new GitRevisionFiles(
        new FakeCommandExecutor(0, LISTING, ""),
        this::missingProcess
    );

    // When
    try (RevisionSnapshot snapshot = files.open(repository, "HEAD")) {
      // Then
      assertThat(snapshot.read("other.java")).isEmpty();
      assertThatThrownBy(() -> snapshot.read("A.java")).isInstanceOf(RevisionReadException.class);
    }
  }

  @Test
  @DisplayName("the git process is closed after an error")
  void processIsClosedAfterAnError() throws Exception {
    // Given
    final GitRevisionFiles files = new GitRevisionFiles(
        new FakeCommandExecutor(0, LISTING, ""),
        this::missingProcess
    );

    // When
    assertThatThrownBy(
        () -> {
          try (RevisionSnapshot snapshot = files.open(repository, "HEAD")) {
            snapshot.read("A.java");
          }
        }
    ).isInstanceOf(RevisionReadException.class);

    // Then
    assertThat(processes).hasSize(1);
    assertThat(processes.getFirst().waitFor(SECONDS, TimeUnit.SECONDS)).isTrue();
  }

  private Process start(final List<String> command, final Path directory) throws IOException {
    started.add(command);
    final Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    processes.add(process);
    return process;
  }

  private Process missingProcess(
      final List<String> command,
      final Path directory
  ) throws IOException {
    return start(
        command.contains("cat-file")
            ? List.of("sh", "-c", "while read id; do echo \"$id missing\"; done")
            : command,
        directory
    );
  }

  private void git(final ProcessCommandExecutor executor, final String... arguments) {
    final List<String> command =
        new ArrayList<>(
            List.of(
                "git",
                "-c",
                "user.name=T",
                "-c",
                "user.email=t@example.com",
                "-c",
                "commit.gpgsign=false"
            )
        );
    command.addAll(List.of(arguments));
    executor.execute(command, repository);
  }
}
