package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.ToolchainSnapshot;
import dev.ward.domain.project.Framework;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Doctor reports the JDK that runs a Maven build and the pinned gate versions it cannot run. */
class DoctorJvmTest {
  private static final int JAVA_7 = 7;
  private static final int JAVA_8 = 8;
  private static final int JAVA_17 = 17;
  private static final int JAVA_27 = 27;
  private static final String JDK = "jdk";
  private static final String SPOTBUGS = "spotbugs";

  @TempDir Path root;

  @BeforeEach
  void writeProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><build><plugins><plugin>"
            + "<artifactId>spotbugs-maven-plugin</artifactId><version>4.9.7.0</version>"
            + "</plugin></plugins></build></project>");
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 2\n\n[checks.spotbugs]\nenabled = true\nprofiles = [\"ci\"]\n"
            + "command = \"mvn spotbugs:check\"\n");
  }

  @Test
  void supportedJdkIsOk() {
    final List<Finding> findings = diagnose(Optional.of(JAVA_17));

    assertThat(findings)
        .filteredOn(finding -> JDK.equals(finding.name()))
        .containsExactly(new Finding(JDK, Status.OK, "JDK 17 runs the build."));
    assertThat(names(findings)).doesNotContain(SPOTBUGS);
  }

  @Test
  void jdkBelow8WarnsWithoutAnError() {
    final List<Finding> findings = diagnose(Optional.of(JAVA_7));

    assertThat(findings)
        .filteredOn(finding -> JDK.equals(finding.name()))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.status()).isEqualTo(Status.WARNING);
              assertThat(finding.message()).contains("Ward supports Java 8 to 25");
            });
    assertThat(findings)
        .filteredOn(finding -> finding.status() == Status.ERROR)
        .extracting(Finding::name)
        .doesNotContain(JDK, SPOTBUGS);
  }

  @Test
  void jdkAbove25WarnsAndChecksPinsAsJdk25() {
    final List<Finding> findings = diagnose(Optional.of(JAVA_27));

    assertThat(findings)
        .filteredOn(finding -> JDK.equals(finding.name()))
        .containsExactly(
            new Finding(
                JDK,
                Status.WARNING,
                "Ward has not been tested on JDK 27 and does not support it; it is configured as"
                    + " JDK 25."));
    assertThat(names(findings)).doesNotContain(SPOTBUGS);
  }

  @Test
  void configuredGateThatDoesNotRunOnTheJdkWarnsWithItsRange() {
    final List<Finding> findings = diagnose(Optional.of(JAVA_8));

    assertThat(findings)
        .filteredOn(finding -> SPOTBUGS.equals(finding.name()))
        .containsExactly(
            new Finding(
                SPOTBUGS,
                Status.WARNING,
                "spotbugs-maven-plugin 4.9.7.0 runs on JDK 11 to 25; this project builds on"
                    + " JDK 8."));
  }

  @Test
  void undeterminedJdkWarnsAndSkipsGateCompatibility() {
    final List<Finding> findings = diagnose(Optional.empty());

    assertThat(findings)
        .filteredOn(finding -> JDK.equals(finding.name()))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.status()).isEqualTo(Status.WARNING);
              assertThat(finding.message()).contains("could not be determined");
            });
    assertThat(names(findings)).doesNotContain(SPOTBUGS);
  }

  private List<Finding> diagnose(final Optional<Integer> jdk) {
    final PomToolConfigurator maven =
        new PomToolConfigurator(
            project ->
                new CompatibilityProjectSnapshot(
                    project,
                    new ToolchainSnapshot(
                        jdk, Optional.empty(), Optional.of("3.9.11"), Framework.NONE),
                    Map.of("spotbugs-maven-plugin", "4" + ".9.7.0")));
    return new DoctorService(
            new ProjectDetector(new FileSystemProjectInspection()),
            directory ->
                new EnvironmentFacts(Set.of("mvn"), Map.of("configurationPresent", "true")),
            new DoctorPorts(new TomlConfigurationReader(), null, maven, null),
            Set.of())
        .diagnose(root);
  }

  private static List<String> names(final List<Finding> findings) {
    return findings.stream().map(Finding::name).toList();
  }
}
