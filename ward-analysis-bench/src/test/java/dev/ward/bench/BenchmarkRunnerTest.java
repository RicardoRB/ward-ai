package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunnerTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String URL = "https://example.com/p.git";
  private static final int BINARY_SIZE = 10;
  private static final int REPEATS = 2;
  private static final String SHA256 = "a".repeat(64);
  private static final Path BINARY = Path.of("/bin/ward");
  private static final CorpusEntry PETCLINIC = new CorpusEntry("java", "petclinic", URL, SHA);

  @TempDir Path dir;

  @Test
  void measuresColdWarmAndEditsForAnEntry() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.head = SHA;

    final BenchmarkResult result =
        new BenchmarkRunner(runner, BINARY, dir.resolve("cache"), dir.resolve("work"), REPEATS)
            .run(List.of(PETCLINIC), new NativeExecutable(BINARY_SIZE, SHA256));

    final BenchmarkResult.JobResult full = result.entries().get(0).jobs().get(0);
    assertThat(full.name()).isEqualTo("ward-check-ci");
    assertThat(full.command()).containsExactly("ward", "check", "--profile", "ci");
    assertThat(full.cold().count()).isEqualTo(REPEATS);
    assertThat(full.warm().count()).isEqualTo(REPEATS);
    assertThat(full.edit()).isNull();

    final BenchmarkResult.JobResult edit = result.entries().get(0).jobs().get(1);
    assertThat(edit.edit().count()).isEqualTo(BenchmarkRunner.EDIT_REPEATS);
    assertThat(result.completed()).isTrue();
  }

  @Test
  void initialisesAnUninitialisedEntryWithTheStandardPreset() {
    final FakeCommandRunner runner = new FakeCommandRunner();
    runner.head = SHA;

    new BenchmarkRunner(runner, BINARY, dir.resolve("cache"), dir.resolve("work"), 1)
        .run(List.of(PETCLINIC), new NativeExecutable(BINARY_SIZE, SHA256));

    assertThat(runner.commands)
        .contains(List.of("/bin/ward", "init", "--preset", "standard", "--yes"));
  }

  @Test
  void editsTheFirstSourceFileInSortedOrderOutsideBuildOutput() throws Exception {
    final Path copy = Files.createDirectories(dir.resolve("copy"));
    Files.createDirectories(copy.resolve("target"));
    Files.writeString(copy.resolve("target/Generated.java"), "x");
    Files.createDirectories(copy.resolve("src"));
    Files.writeString(copy.resolve("src/Zed.java"), "z");
    Files.writeString(copy.resolve("src/Alpha.java"), "a");

    assertThat(SourceEdits.target(copy, "java")).isEqualTo(copy.resolve("src/Alpha.java"));
  }

  @Test
  void checkstyleEditHookRunsOnlyForTheWardEntry() {
    final CorpusEntry ward = new CorpusEntry("java", "ward", URL, SHA);

    assertThat(BenchmarkRunner.jobsFor(ward))
        .extracting(GateJob::name)
        .contains("checkstyle-edit-hook");
    assertThat(BenchmarkRunner.jobsFor(PETCLINIC))
        .extracting(GateJob::name)
        .doesNotContain("checkstyle-edit-hook");
  }

  @Test
  void wardProbeJobRunsForEveryEntryAndNamesTheEditedFile() {
    final CorpusEntry ward = new CorpusEntry("java", "ward", URL, SHA);

    assertThat(BenchmarkRunner.jobsFor(ward))
        .filteredOn(GateJob::wardProbe)
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.name()).isEqualTo("ward-gate-edit");
              assertThat(job.recordedCommand()).endsWith("--path", GateJob.EDITED);
            });
    assertThat(BenchmarkRunner.jobsFor(PETCLINIC)).extracting(GateJob::wardProbe).contains(true);
  }
}
