package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The stryker gate accepts a minimum score from 0 to 100; other gates and values are rejected. */
class TomlMinimumParserTest {
  private static final String STRYKER =
      "version = 2\n[checks.stryker]\ncommand = [\"npx\", \"stryker\", \"run\"]\n";
  private static final int MINIMUM = 60;
  private static final int MAXIMUM = 100;
  private static final String RANGE = "must be an integer from 0 to 100";

  @TempDir Path dir;

  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @Test
  void minimumOnStrykerIsRead() throws IOException {
    assertThat(read(reader, dir, STRYKER + "minimum = 60").checks().get("stryker").minimum())
        .isEqualTo(MINIMUM);
    assertThat(read(reader, dir, STRYKER).checks().get("stryker").minimum()).isNull();
  }

  @Test
  void minimumOnPitIsRead() throws IOException {
    assertThat(
            read(
                    reader,
                    dir,
                    "version = 2\n[checks.mutation]\ncommand = \"./mvnw pitest:mutationCoverage\"\n"
                        + "minimum = 60")
                .checks()
                .get("mutation")
                .minimum())
        .isEqualTo(MINIMUM);
  }

  @Test
  void minimumOnMutmutIsRead() throws IOException {
    assertThat(
            read(
                    reader,
                    dir,
                    "version = 2\n[checks.mutmut]\ncommand = [\"uv\", \"run\", \"mutmut\", \"run\"]\n"
                        + "minimum = 60")
                .checks()
                .get("mutmut")
                .minimum())
        .isEqualTo(MINIMUM);
  }

  @Test
  void swiftCoverageRequiresItsMinimum() throws IOException {
    final String coverage =
        "version = 2\n[checks.coverage]\nuses = \"swift-coverage\"\n"
            + "command = [\"swift\", \"test\", \"--show-codecov-path\"]\n";

    assertThat(read(reader, dir, coverage + "minimum = 60").checks().get("coverage").minimum())
        .isEqualTo(MINIMUM);
    assertThatThrownBy(() -> read(reader, dir, coverage))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.coverage.minimum");
  }

  @Test
  void boundsAreAccepted() throws IOException {
    assertThat(read(reader, dir, STRYKER + "minimum = 0").checks().get("stryker").minimum())
        .isZero();
    assertThat(read(reader, dir, STRYKER + "minimum = 100").checks().get("stryker").minimum())
        .isEqualTo(MAXIMUM);
  }

  @Test
  void minimumOnAnotherGateIsRejectedNamingTheKey() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.vitest]\ncommand = \"npx vitest run\"\n"
                        + "minimum = 60"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.vitest.minimum");
  }

  @Test
  void outOfRangeOrNonIntegerMinimumIsRejected() {
    assertThatThrownBy(() -> read(reader, dir, STRYKER + "minimum = 120"))
        .hasMessageContaining(RANGE);
    assertThatThrownBy(() -> read(reader, dir, STRYKER + "minimum = -1"))
        .hasMessageContaining(RANGE);
    assertThatThrownBy(() -> read(reader, dir, STRYKER + "minimum = \"60\""))
        .hasMessageContaining(RANGE);
    assertThatThrownBy(() -> read(reader, dir, STRYKER + "minimum = 60.5"))
        .hasMessageContaining(RANGE);
  }
}
