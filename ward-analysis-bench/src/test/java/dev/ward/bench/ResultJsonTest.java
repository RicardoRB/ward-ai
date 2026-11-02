package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultJsonTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final int BINARY_SIZE = 42;
  private static final int FULL_REPEATS = 5;
  private static final int EDIT_REPEATS = 20;

  @Test
  void writesSchemaVersionManifestAndCommandVectorsWithoutEnvironmentOrOutput() {
    final Statistics stats = Statistics.of(List.of(1.0, 2.0));
    final BenchmarkResult.JobResult job =
        new BenchmarkResult.JobResult(
            "ward-check-default",
            List.of("ward", "check", "--profile", "default"),
            true,
            List.of(0),
            stats,
            stats,
            stats,
            stats);
    final BenchmarkResult result =
        new BenchmarkResult(
            List.of(
                new BenchmarkResult.EntryResult(
                    new CorpusEntry("java", "petclinic", "https://example.com/p.git", SHA),
                    List.of(job))),
            new NativeExecutable(BINARY_SIZE, "b".repeat(64)),
            FULL_REPEATS,
            EDIT_REPEATS,
            true);

    final String json = ResultJson.render(result, Instant.parse("2026-11-02T00:00:00Z"));

    assertThat(json).contains("\"schemaVersion\": 1");
    assertThat(json).contains("\"commit\": \"" + SHA + "\"");
    assertThat(json).contains("\"command\": [\n");
    assertThat(json).contains("\"ward\"");
    assertThat(json).doesNotContain("PATH").doesNotContain("HOME").doesNotContain("environment");
  }
}
