package dev.ward.infrastructure.finding.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.infrastructure.finding.FindingFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrykerNetMutationReportTest {
  @TempDir Path root;

  private final StrykerNetMutationReport report = new StrykerNetMutationReport(new ForyJsonCodec());

  private MutationCounts read() {
    return report.read(
        new Project(root, Language.CSHARP, BuildTool.DOTNET, Framework.NONE),
        FindingFixtures.justBefore()
    );
  }

  @Test
  void aMissingReportIsUnusable() {
    assertThat(read().problem()).contains("is missing");
  }

  @Test
  void anUnparsableReportIsUnusable() throws IOException {
    write("not json");

    assertThat(read().problem()).contains("cannot be parsed");
  }

  @Test
  void aReportOlderThanTheGateIsUnusable() throws IOException {
    write("{\"files\":{}}");
    FindingFixtures.age(root.resolve(StrykerNetMutationReport.REPORT), Duration.ofHours(1));

    assertThat(read().problem()).contains("before the gate started");
  }

  @Test
  void countsKilledAndSurvivedAndNamesTheSurvivors() throws IOException {
    write(
        "{\"files\":{\"a.cs\":{\"mutants\":[{\"status\":\"Killed\"},{\"status\":\"Timeout\"},"
            + "{\"status\":\"NoCoverage\",\"mutatorName\":\"Block\","
            + "\"location\":{\"start\":{\"line\":4}}},{\"status\":\"Ignored\"}]}}}"
    );

    final MutationCounts counts = read();

    assertThat(counts.detected()).isEqualTo(2);
    assertThat(counts.undetected()).isEqualTo(1);
    assertThat(counts.survivors()).containsExactly("a.cs:4 Block");
  }

  private void write(final String json) throws IOException {
    final Path file = root.resolve(StrykerNetMutationReport.REPORT);
    Files.createDirectories(file.getParent());
    Files.writeString(file, json);
  }
}
