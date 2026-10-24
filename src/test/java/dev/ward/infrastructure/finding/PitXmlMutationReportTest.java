package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.MutationCounts;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PitXmlMutationReportTest {
  private static final String MAVEN_REPORT = "target/pit-reports/mutations.xml";
  private static final int DETECTED = 3;
  private static final int UNDETECTED = 2;
  // KILLED, TIMED_OUT and NON_VIABLE are detected in PIT's own terms; SURVIVED and NO_COVERAGE not.
  private static final String REPORT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <mutations partial="false">
      <mutation detected='true' status='KILLED' numberOfTestsRun='2'><sourceFile>A.java</sourceFile></mutation>
      <mutation detected='true' status='TIMED_OUT' numberOfTestsRun='1'><sourceFile>A.java</sourceFile></mutation>
      <mutation detected='true' status='NON_VIABLE' numberOfTestsRun='0'><sourceFile>B.java</sourceFile></mutation>
      <mutation detected='false' status='SURVIVED' numberOfTestsRun='3'><sourceFile>B.java</sourceFile></mutation>
      <mutation detected='false' status='NO_COVERAGE' numberOfTestsRun='0'><sourceFile>C.java</sourceFile></mutation>
      </mutations>
      """;

  @TempDir Path root;

  private final PitXmlMutationReport report = new PitXmlMutationReport();

  @Test
  void mavenReportCountsDetectedAgainstAllMutations() {
    FindingFixtures.write(root.resolve(MAVEN_REPORT), REPORT);

    final MutationCounts counts =
        report.read(FindingFixtures.maven(root), FindingFixtures.justBefore());

    assertThat(counts.usable()).isTrue();
    assertThat(counts.detected()).isEqualTo(DETECTED);
    assertThat(counts.undetected()).isEqualTo(UNDETECTED);
  }

  @Test
  void gradleReportLivesUnderBuildReports() {
    FindingFixtures.write(root.resolve("build/reports/pitest/mutations.xml"), REPORT);

    final MutationCounts counts =
        report.read(FindingFixtures.gradle(root), FindingFixtures.justBefore());

    assertThat(counts.valid()).isEqualTo(DETECTED + UNDETECTED);
  }

  @Test
  void missingReportExplainsWhichFileIsExpected() {
    assertThat(report.read(FindingFixtures.maven(root), FindingFixtures.justBefore()).problem())
        .contains(MAVEN_REPORT, "missing");
  }

  @Test
  void staleReportIsUnusable() {
    FindingFixtures.write(root.resolve(MAVEN_REPORT), REPORT);
    FindingFixtures.age(root.resolve(MAVEN_REPORT), Duration.ofHours(1));

    assertThat(report.read(FindingFixtures.maven(root), FindingFixtures.justBefore()).problem())
        .contains("before the gate started");
  }

  @Test
  void malformedReportIsUnusable() {
    FindingFixtures.write(root.resolve(MAVEN_REPORT), "<mutations><mutation detected='true'");

    assertThat(report.read(FindingFixtures.maven(root), FindingFixtures.justBefore()).problem())
        .contains("cannot be parsed");
  }

  @Test
  void reportWithoutMutationsHasNoValidMutants() {
    FindingFixtures.write(root.resolve(MAVEN_REPORT), "<mutations partial=\"false\"></mutations>");

    final MutationCounts counts =
        report.read(FindingFixtures.maven(root), FindingFixtures.justBefore());

    assertThat(counts.usable()).isTrue();
    assertThat(counts.valid()).isZero();
  }
}
