package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the accuracy mode offline: the ward run is a fake that answers with canned JSON. */
class AccuracyRunTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String RULE = "ward:java/sql-injection";
  private static final String FOUND =
      "{\"checks\":[{\"status\":\"FAILED\",\"findings\":["
          + "{\"rule\":\"ward:java/sql-injection\",\"file\":\"Search.java\"}],"
          + "\"findingsTruncated\":false}]}";

  @TempDir Path dir;

  @Test
  void aCheckedInCorpusIsScoredByAWardRunThatSelectsItsRules() throws IOException {
    final Path accuracy = dir.resolve("accuracy");
    final Path corpus = accuracy.resolve("tiny");
    Files.createDirectories(corpus);
    Files.writeString(corpus.resolve("Search.java"), "class Search {}\n");
    Files.writeString(
        corpus.resolve("labels.csv"), "file,rule,vulnerable\nSearch.java," + RULE + ",true\n");
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.wardOutput = FOUND;

    final AccuracyResult result =
        new AccuracyRun(runner, Path.of("ward"), dir.resolve("cache"), dir.resolve("work"))
            .run(accuracy);

    assertThat(result.corpora()).hasSize(1);
    final CorpusAccuracy scored = result.corpora().get(0);
    assertThat(scored.name()).isEqualTo("tiny");
    assertThat(scored.commit()).isEqualTo(CorpusAccuracy.CHECKED_IN);
    assertThat(scored.rules()).containsExactly(new RuleScore(RULE, 1, 0, 0));
    assertThat(runner.commands.get(runner.commands.size() - 1))
        .containsExactly("ward", "check", "--format", "json", "--profile", AccuracyRun.PROFILE);
    assertThat(Files.readString(dir.resolve("work/tiny/shard-0/ward.toml")))
        .contains("rules = [\"" + RULE + "\"]")
        .contains("profiles = [\"accuracy\"]");
  }

  @Test
  void aCorpusWithNoLabelsIsRefused() throws IOException {
    final Path accuracy = dir.resolve("accuracy");
    Files.createDirectories(accuracy.resolve("empty"));
    Files.writeString(accuracy.resolve("empty/labels.csv"), "file,rule,vulnerable\n");

    assertThatThrownBy(
            () ->
                new AccuracyRun(
                        new FakeCommandRunner(),
                        Path.of("ward"),
                        dir.resolve("c"),
                        dir.resolve("w"))
                    .run(accuracy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no labels");
  }

  @Test
  void aWardRunThatPrintsNothingIsRefused() throws IOException {
    final Path accuracy = dir.resolve("accuracy");
    Files.createDirectories(accuracy.resolve("tiny"));
    Files.writeString(accuracy.resolve("tiny/A.java"), "class A {}\n");
    Files.writeString(
        accuracy.resolve("tiny/labels.csv"), "file,rule,vulnerable\nA.java," + RULE + ",true\n");

    assertThatThrownBy(
            () ->
                new AccuracyRun(
                        new FakeCommandRunner(),
                        Path.of("ward"),
                        dir.resolve("c"),
                        dir.resolve("w"))
                    .run(accuracy))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("printed no JSON");
  }

  @Test
  void anExternalCorpusWhoseCommitCannotBeFetchedFailsNamingItsRepositoryAndCommit()
      throws IOException {
    final Path accuracy = dir.resolve("accuracy");
    Files.createDirectories(accuracy);
    Files.writeString(
        accuracy.resolve("external.csv"),
        "name,url,commit,labels,format,root\n"
            + "owasp,https://example.com/owasp.git,"
            + SHA
            + ",expectedresults.csv,owasp-benchmark,src\n");
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.fetchExitCode = 1;

    assertThatThrownBy(
            () ->
                new AccuracyRun(runner, Path.of("ward"), dir.resolve("c"), dir.resolve("w"))
                    .run(accuracy))
        .isInstanceOf(CorpusFetchException.class)
        .hasMessageContaining("owasp")
        .hasMessageContaining(SHA)
        .hasMessageContaining("does not fall back");
    assertThat(runner.commands).extracting(command -> command.get(0)).contains("git");
  }
}
