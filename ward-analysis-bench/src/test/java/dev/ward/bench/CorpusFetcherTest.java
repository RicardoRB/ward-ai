package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusFetcherTest {

  private static final int GIT_FAILURE_EXIT = 128;
  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String OTHER_SHA = "ffffffffffffffffffffffffffffffffffffffff";
  private static final CorpusEntry ENTRY =
      new CorpusEntry("java", "petclinic", "https://example.com/p.git", SHA);

  @TempDir Path cache;

  @Test
  void fetchesTheFullPinnedShaWithArgumentVectorsOnly() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.head = SHA;

    final Path checkout = new CorpusFetcher(runner).fetch(ENTRY, cache);

    assertThat(checkout).isEqualTo(cache.resolve("petclinic"));
    assertThat(runner.commands)
        .containsExactly(
            List.of("git", "init", "--quiet"),
            List.of("git", "fetch", "--depth", "1", "--quiet", "https://example.com/p.git", SHA),
            List.of("git", "checkout", "--quiet", "FETCH_HEAD"),
            List.of("git", "rev-parse", "HEAD"));
  }

  @Test
  void neverFetchesABranchName() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.head = SHA;

    new CorpusFetcher(runner).fetch(ENTRY, cache);

    assertThat(runner.commands)
        .noneMatch(command -> command.contains("main") || command.contains("HEAD~"));
  }

  @Test
  void failedFetchNamesTheRepositoryAndCommit() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.fetchExitCode = GIT_FAILURE_EXIT;

    assertThatThrownBy(() -> new CorpusFetcher(runner).fetch(ENTRY, cache))
        .isInstanceOf(CorpusFetchException.class)
        .hasMessageContaining("petclinic")
        .hasMessageContaining(SHA)
        .hasMessageContaining("does not fall back");
  }

  @Test
  void mismatchedCheckoutFailsWithoutSubstitution() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.head = OTHER_SHA;

    assertThatThrownBy(() -> new CorpusFetcher(runner).fetch(ENTRY, cache))
        .isInstanceOf(CorpusFetchException.class)
        .hasMessageContaining(SHA)
        .hasMessageContaining("resolved to " + OTHER_SHA);
  }
}
