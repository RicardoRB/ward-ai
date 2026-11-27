package dev.ward.infrastructure.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.secrets.HistoryPatch;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** A history batch or commit over the size limit is split, read file by file, or skipped. */
class GitCliHistorySplitTest {
  private static final String MARKER = "\u0001ward-commit ";
  private static final String COMMIT = "0123456789abcdef\n";
  private static final String BIG = "x".repeat(GitCliHistory.BATCH_LIMIT + 1);
  private static final String LOG =
      MARKER
          + "0123456789abcdef\ndiff --git a/c.yml b/c.yml\n--- a/c.yml\n+++ b/c.yml\n"
          + "@@ -1,0 +5,2 @@\n+one\n+two\n";
  private static final LocalDate SINCE = LocalDate.of(2026, 1, 1);
  private static final Duration TIME = Duration.ofMillis(1);
  private static final int GIT_FATAL = 128;
  private static final int HALF = 100;
  private static final int LAST_SPLIT = 8;
  private static final int THIRD = 2;
  private static final int FOURTH = 3;
  private static final int FIFTH = 4;

  @Test
  void aBatchOverTheSizeCapIsHalvedAndReadAgainFromTheSamePosition() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(ok("false"), ok(BIG), ok(LOG), ok(""));
    final List<List<HistoryPatch>> batches = new ArrayList<>();

    new GitCliHistory(executor).patches(Path.of("."), Optional.empty(), batches::add);

    assertThat(executor.commands.get(1)).contains("--max-count=200", "--skip=0");
    assertThat(executor.commands.get(2)).contains("--max-count=100", "--skip=0");
    assertThat(executor.commands.get(THIRD + 1)).contains("--max-count=200", "--skip=" + HALF);
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0).get(0).commit()).isEqualTo("01234567");
  }

  @Test
  void aSingleCommitOverTheLimitIsReadFileByFile() {
    final FakeCommandExecutor executor = oneLargeCommit(
        ok(COMMIT),
        ok("c.yml\0d.yml\0"),
        ok(LOG),
        ok(LOG)
    );
    final List<List<HistoryPatch>> batches = new ArrayList<>();

    new GitCliHistory(executor).patches(Path.of("."), Optional.of(SINCE), batches::add);

    assertThat(batches).hasSize(2);
    assertThat(executor.commands.get(LAST_SPLIT + 1))
        .containsSubsequence("git", "rev-list", "--all")
        .contains("--max-count=1", "--skip=0", "--since=2026-01-01");
    assertThat(executor.commands.get(LAST_SPLIT + THIRD)).contains("diff-tree", "--name-only");
    assertThat(executor.commands.get(LAST_SPLIT + FOURTH))
        .contains("diff-tree", "0123456789abcdef", ":(literal)c.yml");
    assertThat(executor.commands.get(LAST_SPLIT + FIFTH)).contains(":(literal)d.yml");
    assertThat(String.join(" ", executor.commands.get(LAST_SPLIT)))
        .contains("--max-count=1");
  }

  @Test
  void aFileStillOverTheLimitSkipsThatFileWithAWarningAndKeepsTheRest() {
    final FakeCommandExecutor executor = oneLargeCommit(
        ok(COMMIT),
        ok("c.yml\0d.yml\0"),
        ok(LOG),
        ok(BIG)
    );
    final List<List<HistoryPatch>> batches = new ArrayList<>();
    final List<String> warnings = captureWarnings();

    new GitCliHistory(executor).patches(Path.of("."), Optional.empty(), batches::add);

    assertThat(batches).hasSize(1);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0))
        .contains("commit=01234567", "files=1")
        .doesNotContain("one")
        .doesNotContain("c.yml");
  }

  @Test
  void aCommitThatCannotBeListedIsSkippedWithAWarningNotAnError() {
    final List<String> warnings = captureWarnings();
    final CommandResult failed = new CommandResult(GIT_FATAL, "", "fatal", TIME);
    final List<List<HistoryPatch>> batches = new ArrayList<>();

    new GitCliHistory(oneLargeCommit(failed, ok("c.yml\0")))
        .patches(Path.of("."), Optional.empty(), batches::add);
    new GitCliHistory(oneLargeCommit(ok(COMMIT), failed))
        .patches(Path.of("."), Optional.empty(), batches::add);

    assertThat(batches).isEmpty();
    assertThat(warnings).hasSize(2).allMatch(line -> line.contains("secrets-history-skip"));
  }

  private static FakeCommandExecutor oneLargeCommit(
      final CommandResult revList,
      final CommandResult names,
      final CommandResult... files
  ) {
    final List<CommandResult> results = new ArrayList<>();
    results.add(ok("false"));
    results.addAll(Collections.nCopies(LAST_SPLIT, ok(BIG)));
    results.add(revList);
    results.add(names);
    results.addAll(List.of(files));
    results.add(ok(""));
    return new FakeCommandExecutor(results.toArray(new CommandResult[0]));
  }

  private static CommandResult ok(final String stdout) {
    return new CommandResult(0, stdout, "", TIME);
  }

  private static List<String> captureWarnings() {
    final List<String> warnings = new ArrayList<>();
    final Logger logger = Logger.getLogger(LargeCommitReader.class.getName());
    logger.addHandler(
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            if (record.getLevel() == Level.WARNING) {
              warnings.add(record.getMessage());
            }
          }

          @Override
          public void flush() {
            // nothing is buffered
          }

          @Override
          public void close() {
            // nothing is held
          }
        }
    );
    return warnings;
  }
}
