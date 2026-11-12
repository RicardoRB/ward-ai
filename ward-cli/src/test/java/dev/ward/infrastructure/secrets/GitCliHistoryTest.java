package dev.ward.infrastructure.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryUnavailableException;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code git} adapter of the history scan, with a fake executor and with a real repository. */
class GitCliHistoryTest {

  private static final String MARKER = "\u0001ward-commit ";
  private static final String TOKEN = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";
  private static final Duration TIME = Duration.ofMillis(1);
  private static final String LOG =
      MARKER
          + "0123456789abcdef\ndiff --git a/c.yml b/c.yml\n--- a/c.yml\n+++ b/c.yml\n"
          + "@@ -1,0 +5,2 @@\n+one\n+two\n";

  @Test
  void asksGitForTheShallowStateThenReadsTheLogInBatchesWithSince() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            new CommandResult(0, "false\n", "", TIME),
            new CommandResult(0, LOG, "", TIME),
            new CommandResult(0, "", "", TIME));
    final List<List<HistoryPatch>> batches = new ArrayList<>();

    new GitCliHistory(executor)
        .patches(Path.of("."), Optional.of(LocalDate.of(2026, 1, 1)), batches::add);

    assertThat(executor.commands.get(0))
        .containsExactly("git", "rev-parse", "--is-shallow-repository");
    assertThat(executor.commands.get(1))
        .containsSubsequence("git", "log", "--all", "-p", "-U0", "--no-color", "--no-ext-diff")
        .contains("--max-count=200", "--skip=0", "--since=2026-01-01", "--no-textconv");
    assertThat(executor.commands.get(2)).contains("--skip=200");
    assertThat(batches).hasSize(1);
    final HistoryPatch patch = batches.get(0).get(0);
    assertThat(patch.commit()).isEqualTo("01234567");
    assertThat(patch.path()).isEqualTo("c.yml");
    assertThat(patch.lines().get(0).number()).isEqualTo(5);
    assertThat(patch.lines().get(1).text()).isEqualTo("two");
  }

  @Test
  void aShallowCloneIsAnErrorTellingTheUserToFetchTheFullHistory() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "true\n", "");

    assertThatThrownBy(
            () -> new GitCliHistory(executor).patches(Path.of("."), Optional.empty(), b -> {}))
        .isInstanceOf(HistoryUnavailableException.class)
        .hasMessageContaining("shallow")
        .hasMessageContaining("fetch-depth: 0");
  }

  @Test
  void gitThatCannotStartOrFailsIsAnError() {
    final CommandResult missing = new CommandResult(-1, "", "", TIME, "could not start git");

    assertThatThrownBy(
            () ->
                new GitCliHistory(new FakeCommandExecutor(missing))
                    .patches(Path.of("."), Optional.empty(), b -> {}))
        .isInstanceOf(HistoryUnavailableException.class)
        .hasMessageContaining("git");
    assertThatThrownBy(
            () ->
                new GitCliHistory(
                        new FakeCommandExecutor(
                            new CommandResult(0, "false", "", TIME),
                            new CommandResult(128, "", "fatal", TIME)))
                    .patches(Path.of("."), Optional.empty(), b -> {}))
        .isInstanceOf(HistoryUnavailableException.class);
  }

  @Test
  void aBatchOverTheSizeCapNamesTheCommit() {
    final String big = LOG + "+" + "x".repeat(GitCliHistory.BATCH_LIMIT);

    assertThatThrownBy(
            () ->
                new GitCliHistory(
                        new FakeCommandExecutor(
                            new CommandResult(0, "false", "", TIME),
                            new CommandResult(0, big, "", TIME)))
                    .patches(Path.of("."), Optional.empty(), b -> {}))
        .isInstanceOf(HistoryUnavailableException.class)
        .hasMessageContaining("01234567");
  }

  @Test
  void aTokenAddedAndRemovedLaterIsFoundInARealRepository(@TempDir final Path repo)
      throws Exception {
    git(repo, "init", "-q", "-b", "main");
    git(repo, "config", "user.email", "t@example.com");
    git(repo, "config", "user.name", "t");
    Files.writeString(repo.resolve("c.yml"), "a: 1\ntoken: " + TOKEN + "\n");
    git(repo, "add", ".");
    git(repo, "commit", "-q", "-m", "add");
    Files.writeString(repo.resolve("c.yml"), "a: 1\n");
    git(repo, "commit", "-q", "-am", "remove");
    final List<HistoryPatch> found = new ArrayList<>();

    new GitCliHistory(new ProcessCommandExecutor()).patches(repo, Optional.empty(), found::addAll);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).path()).isEqualTo("c.yml");
    assertThat(found.get(0).lines().get(1).number()).isEqualTo(2);
    assertThat(found.get(0).lines().get(1).text()).contains(TOKEN);
    found.clear();
    new GitCliHistory(new ProcessCommandExecutor())
        .patches(repo, Optional.of(LocalDate.now().plusDays(1)), found::addAll);
    assertThat(found).isEmpty();
  }

  @Test
  void aShallowCloneOfARealRepositoryIsAnError(@TempDir final Path work) throws Exception {
    final Path origin = work.resolve("origin");
    Files.createDirectories(origin);
    git(origin, "init", "-q", "-b", "main");
    git(origin, "config", "user.email", "t@example.com");
    git(origin, "config", "user.name", "t");
    for (final String name : List.of("a", "b")) {
      Files.writeString(origin.resolve(name), name);
      git(origin, "add", ".");
      git(origin, "commit", "-q", "-m", name);
    }
    git(work, "clone", "-q", "--depth", "1", origin.toUri().toString(), "clone");

    assertThatThrownBy(
            () ->
                new GitCliHistory(new ProcessCommandExecutor())
                    .patches(work.resolve("clone"), Optional.empty(), b -> {}))
        .isInstanceOf(HistoryUnavailableException.class)
        .hasMessageContaining("shallow");
  }

  private static void git(final Path directory, final String... arguments)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(arguments));
    final Process process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    assertThat(process.waitFor()).as(String.join(" ", command)).isZero();
  }
}
