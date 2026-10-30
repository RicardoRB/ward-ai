package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutmutMetaMutationReportTest {
  private static final Path SPIKE_META = Path.of("src/test/resources/fixtures/mutmut/calc.py.meta");
  // The spike fixture holds one killed (exit 1), one survived (0), one timed out (-24) and one
  // no-tests (33) mutant: two detected and two undetected.
  private static final int SPIKE_DETECTED = 2;
  private static final int SPIKE_UNDETECTED = 2;
  private static final String ONE_FILE_SKIPPED =
      "{\"exit_code_by_key\": {\"a.x\": 34, \"a.y\": null, \"a.z\": 2, \"a.w\": 1}}";

  @TempDir Path root;

  private final MutmutMetaMutationReport report = new MutmutMetaMutationReport(new ForyJsonCodec());

  private MutationCounts read() {
    return report.read(FindingFixtures.python(root), FindingFixtures.justBefore());
  }

  private Path resultFile(final String relative) {
    return root.resolve("mutants").resolve(relative);
  }

  private void writeSpikeFixture() throws IOException {
    FindingFixtures.write(resultFile("src/demo/calc.py.meta"), Files.readString(SPIKE_META));
  }

  @Test
  void countsDetectedAndUndetectedMutantsOfTheSpikeResults() throws IOException {
    writeSpikeFixture();

    final MutationCounts counts = read();

    assertThat(counts.usable()).isTrue();
    assertThat(counts.detected()).isEqualTo(SPIKE_DETECTED);
    assertThat(counts.undetected()).isEqualTo(SPIKE_UNDETECTED);
  }

  @Test
  void skippedNotCheckedAndUnknownMutantsDoNotCount() {
    FindingFixtures.write(resultFile("src/demo/calc.py.meta"), ONE_FILE_SKIPPED);

    final MutationCounts counts = read();

    assertThat(counts.usable()).isTrue();
    assertThat(counts.valid()).isOne();
    assertThat(counts.detected()).isOne();
    assertThat(counts.undetected()).isZero();
  }

  @Test
  void resultsSpreadAcrossFilesAreAllCounted() throws IOException {
    writeSpikeFixture();
    FindingFixtures.write(resultFile("src/demo/other.py.meta"), ONE_FILE_SKIPPED);

    final MutationCounts counts = read();

    assertThat(counts.detected()).isEqualTo(SPIKE_DETECTED + 1);
    assertThat(counts.undetected()).isEqualTo(SPIKE_UNDETECTED);
  }

  @Test
  void missingResultsAreUnusable() {
    assertThat(read().problem()).contains("mutants/", "missing");
  }

  @Test
  void staleResultsAreUnusable() throws IOException {
    writeSpikeFixture();
    FindingFixtures.age(resultFile("src/demo/calc.py.meta"), Duration.ofHours(1));

    assertThat(read().problem()).contains("before the gate started");
  }

  @Test
  void unparseableResultsAreUnusable() {
    FindingFixtures.write(resultFile("src/demo/calc.py.meta"), "{\"exit_code_by_key\": [not json");

    assertThat(read().problem()).contains("src/demo", "cannot be parsed");
  }

  @Test
  void resultsWithOnlySkippedMutantsHaveNoValidMutants() {
    FindingFixtures.write(
        resultFile("src/demo/calc.py.meta"), "{\"exit_code_by_key\": {\"a.x\": 34}}");

    final MutationCounts counts = read();

    assertThat(counts.usable()).isTrue();
    assertThat(counts.valid()).isZero();
  }
}
