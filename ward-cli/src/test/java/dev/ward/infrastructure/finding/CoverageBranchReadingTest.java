package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineHits;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Branch counts come from JaCoCo's {@code mb} and {@code cb} and from lcov's {@code BRDA}. */
class CoverageBranchReadingTest {
  private static final int BRANCH_LINE = 5;
  private static final int THREE = 3;
  private static final Path DIRECTORY = Path.of("src", "test", "resources", "coverage");

  private final DetectedLineCoverage reader =
      new DetectedLineCoverage(List.of(new JacocoXmlLineCoverage(), new LcovLineCoverage()));

  @TempDir Path temp;

  @Test
  void jacocoReportsOfMavenAndGradleCountTheBranchesOfEachLine() {
    for (final String name : List.of("jacoco-maven.xml", "jacoco-gradle.xml")) {
      final LineHits hits = reader.read(DIRECTORY.resolve(name));
      final FileLines lines = hits.entries().get(0).lines();

      assertThat(hits.recordsBranches()).as(name).isTrue();
      assertThat(lines.branches()).containsOnly(java.util.Map.entry(BRANCH_LINE, 2));
      assertThat(lines.branchCount()).isEqualTo(2);
      assertThat(lines.coveredBranchCount()).isEqualTo(1);
    }
  }

  @Test
  void vitestLcovCountsItsBranchRecords() {
    final LineHits hits = reader.read(DIRECTORY.resolve("lcov-vitest.info"));

    assertThat(hits.recordsBranches()).isTrue();
    assertThat(hits.entries().get(0).lines().branchCount()).isEqualTo(2);
    assertThat(hits.entries().get(0).lines().coveredBranchCount()).isEqualTo(1);
  }

  @Test
  void jestLcovTreatsADashAsNotCovered() {
    final LineHits hits = reader.read(DIRECTORY.resolve("lcov-jest.info"));
    final FileLines lines = hits.entries().get(0).lines();

    assertThat(hits.recordsBranches()).isTrue();
    assertThat(lines.branchCount()).isEqualTo(THREE);
    assertThat(lines.coveredBranchCount()).isEqualTo(1);
  }

  @Test
  void pytestLcovWithoutBranchRecordsRecordsNoBranches() {
    final LineHits hits = reader.read(DIRECTORY.resolve("coverage-pytest.lcov"));

    assertThat(hits.recordsBranches()).isFalse();
    assertThat(hits.entries().get(0).lines().branchCount()).isZero();
  }

  @Test
  void aBranchFoundRecordWithNoBranchesIsStillABranchReport() throws IOException {
    final Path report =
        Files.writeString(
            temp.resolve("lcov.info"), "SF:a.ts\nDA:1,1\nBRF:0\nBRH:0\nend_of_record\n");

    final LineHits hits = reader.read(report);

    assertThat(hits.recordsBranches()).isTrue();
    assertThat(hits.entries().get(0).lines().branchCount()).isZero();
  }

  @Test
  void aTruncatedLcovCountsNoPartOfItsLastRecord() throws IOException {
    final Path report =
        Files.writeString(temp.resolve("lcov.info"), "SF:a.ts\nDA:1,1\nBRDA:1,0,0,1\n");

    assertThatThrownBy(() -> reader.read(report))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("truncated");
  }

  @Test
  void aMalformedBranchLineIsRejected() throws IOException {
    final Path report =
        Files.writeString(temp.resolve("lcov.info"), "SF:a.ts\nBRDA:1,0\nend_of_record\n");

    assertThatThrownBy(() -> reader.read(report))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("BRDA");
  }
}
