package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A {@code coverage} check takes its minimums and report from ward.toml. */
class TomlCoverageCheckTest {
  private static final String CHECK = "version = 2\n[checks.coverage]\nuses = \"coverage\"\n";
  private static final int SEVENTY = 70;
  private static final int SEVENTY_FIVE = 75;

  @TempDir Path dir;

  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  private GateConfiguration coverage(final String body) throws IOException {
    return read(reader, dir, CHECK + body).checks().get("coverage");
  }

  @Test
  void minimumBranchesAndReportAreRead() throws IOException {
    final GateConfiguration check =
        coverage("minimum = 70\nminimum-branches = 75\nreport = \"reports/lcov.info\"");

    assertThat(check.type()).isEqualTo("coverage");
    assertThat(check.command()).isEmpty();
    assertThat(check.minimum()).isEqualTo(SEVENTY);
    assertThat(check.coverage().minimumBranches()).isEqualTo(SEVENTY_FIVE);
    assertThat(check.coverage().reports()).containsExactly("reports/lcov.info");
  }

  @Test
  void withoutBranchesAndReportTheDefaultsApply() throws IOException {
    final GateConfiguration check = coverage("minimum = 70");

    assertThat(check.coverage().minimumBranches()).isNull();
    assertThat(check.coverage().reports()).isEmpty();
  }

  @Test
  void reportMayBeAnArray() throws IOException {
    assertThat(
            coverage("minimum = 70\nreport = [\"a/lcov.info\", \"b/lcov.info\"]")
                .coverage()
                .reports())
        .containsExactly("a/lcov.info", "b/lcov.info");
  }

  @Test
  void aMissingMinimumIsAConfigurationErrorNamingTheCheck() {
    assertThatThrownBy(() -> coverage(""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.coverage.minimum")
        .hasMessageContaining("required");
  }

  @Test
  void anOutOfRangeMinimumIsRejected() {
    assertThatThrownBy(() -> coverage("minimum = 101"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("from 0 to 100");
    assertThatThrownBy(() -> coverage("minimum = 70\nminimum-branches = -1"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.coverage.minimum-branches");
  }

  @Test
  void aReportOutsideTheProjectIsRejectedNamingTheKey() {
    assertThatThrownBy(() -> coverage("minimum = 70\nreport = \"../other/lcov.info\""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.coverage.report");
  }

  @Test
  void aCommandThatRunsTheReportGoalInfersTheType() throws IOException {
    final GateConfiguration check =
        read(
                reader,
                dir,
                "version = 2\n[checks.coverage]\ncommand = \"./mvnw jacoco:report\"\nminimum = 70")
            .checks()
            .get("coverage");

    assertThat(check.type()).isEqualTo("coverage");
    assertThat(check.command()).isEqualTo(List.of("./mvnw", "jacoco:report"));
  }

  @Test
  void anOldVerificationCommandIsAPlainCommandCheck() throws IOException {
    final GateConfiguration check =
        read(reader, dir, "version = 2\n[checks.coverage]\ncommand = \"./mvnw jacoco:check\"\n")
            .checks()
            .get("coverage");

    assertThat(check.type()).isEqualTo("command");
    assertThat(check.coverage()).isNull();
  }

  @Test
  void aBranchMinimumOnAnotherTypeIsRejected() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.coverage]\nuses = \"swift-coverage\"\n"
                        + "command = [\"swift\", \"test\"]\nminimum = 60\nminimum-branches = 50"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("minimum-branches")
        .hasMessageContaining("only for the coverage check");
  }
}
