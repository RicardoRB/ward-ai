package dev.ward.infrastructure.finding.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.finding.FindingFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** cargo-mutants' result lists give the detected, missed and ignored mutants. */
class CargoMutantsReportTest {
  private static final int DETECTED = 3;
  private static final String MISSED = "src/a.rs:4:5: replace f -> bool with true";

  @TempDir Path root;

  private final CargoMutantsReport report = new CargoMutantsReport();

  private MutationCounts read() {
    return report.read(
        new Project(root, Language.RUST, BuildTool.CARGO, Framework.NONE),
        FindingFixtures.justBefore()
    );
  }

  private void write(final String name, final String text) throws IOException {
    FindingFixtures.write(root.resolve("mutants.out").resolve(name), text);
  }

  @Test
  void caughtAndTimedOutMutantsAreDetectedAndMissedOnesSurvive() throws IOException {
    write("caught.txt", "src/a.rs:1:1: x\nsrc/a.rs:2:1: y\n");
    write("timeout.txt", "src/a.rs:3:1: z\n");
    write("missed.txt", MISSED + "\n\n");
    write("unviable.txt", "src/a.rs:9:1: not compiled\n");

    final MutationCounts counts = read();

    assertThat(counts.detected()).isEqualTo(DETECTED);
    assertThat(counts.undetected()).isEqualTo(1);
    assertThat(counts.survivors()).containsExactly(MISSED);
  }

  @Test
  void aRunWithoutMissedFileHasNoSurvivors() throws IOException {
    write("caught.txt", "src/a.rs:1:1: x\n");

    assertThat(read().survivors()).isEmpty();
    assertThat(read().usable()).isTrue();
  }

  @Test
  void missingResultsAreUnusable() {
    assertThat(read().usable()).isFalse();
    assertThat(read().problem()).contains("mutants.out/", "missing");
  }

  @Test
  void resultsOlderThanTheGateAreUnusable() throws IOException {
    write("caught.txt", "");
    Files.setLastModifiedTime(
        root.resolve("mutants.out/caught.txt"),
        java.nio.file.attribute.FileTime.fromMillis(0)
    );

    assertThat(read().problem()).contains("before the gate started");
  }
}
