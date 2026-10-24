package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrykerJsonMutationReportTest {
  private static final int DETECTED = 6;
  private static final int UNDETECTED = 4;
  // 5 killed and 1 timeout are detected; 3 survived and 1 no-coverage are undetected.
  private static final String REPORT =
      """
      {"schemaVersion":"2","thresholds":{"high":80,"low":60},"files":{
        "src/a.ts":{"language":"typescript","source":"","mutants":[
          {"id":"1","status":"Killed"},{"id":"2","status":"Killed"},{"id":"3","status":"Killed"},
          {"id":"4","status":"Timeout"},{"id":"5","status":"Survived"},{"id":"6","status":"Ignored"}]},
        "src/b.ts":{"language":"typescript","source":"","mutants":[
          {"id":"7","status":"Killed"},{"id":"8","status":"Killed"},{"id":"9","status":"Survived"},
          {"id":"10","status":"Survived"},{"id":"11","status":"NoCoverage"},
          {"id":"12","status":"CompileError"},{"id":"13","status":"RuntimeError"},
          {"id":"14","status":"Pending"},{"id":"15","status":"Mystery"}]}}}
      """;

  @TempDir Path root;

  private final StrykerJsonMutationReport report =
      new StrykerJsonMutationReport(new ForyJsonCodec());

  private MutationCounts read() {
    return report.read(FindingFixtures.typescript(root), FindingFixtures.justBefore());
  }

  private Path reportFile() {
    return root.resolve(StrykerJsonMutationReport.REPORT);
  }

  @Test
  void countsDetectedAndUndetectedMutantsAndExcludesTheRest() {
    FindingFixtures.write(reportFile(), REPORT);

    final MutationCounts counts = read();

    assertThat(counts.usable()).isTrue();
    assertThat(counts.detected()).isEqualTo(DETECTED);
    assertThat(counts.undetected()).isEqualTo(UNDETECTED);
  }

  @Test
  void missingReportIsUnusable() {
    assertThat(read().problem()).contains("reports/mutation/mutation.json", "missing");
  }

  @Test
  void staleReportIsUnusable() {
    FindingFixtures.write(reportFile(), REPORT);
    FindingFixtures.age(reportFile(), Duration.ofHours(1));

    assertThat(read().problem()).contains("before the gate started");
  }

  @Test
  void malformedReportIsUnusable() {
    FindingFixtures.write(reportFile(), "{\"files\": [not json");

    assertThat(read().problem()).contains("cannot be parsed");
  }

  @Test
  void reportWithoutFilesHasNoValidMutants() {
    FindingFixtures.write(reportFile(), "{\"schemaVersion\":\"2\"}");

    final MutationCounts counts = read();

    assertThat(counts.usable()).isTrue();
    assertThat(counts.valid()).isZero();
  }
}
