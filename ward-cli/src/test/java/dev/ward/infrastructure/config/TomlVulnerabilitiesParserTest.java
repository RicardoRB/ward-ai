package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.VulnerabilityOptions;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlVulnerabilitiesParserTest {
  private static final int AGE = 3;
  private static final String HEAD = "version = 2\n\n[checks.vulnerabilities]\n";
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  private GateConfiguration check(final String body) throws IOException {
    return read(reader, dir, HEAD + body).checks().get("vulnerabilities");
  }

  @Test
  void acceptsTheCheckWithoutACommandAndFillsTheDefaults() throws IOException {
    final GateConfiguration check = check("uses = \"vulnerabilities\"\nprofiles = [\"ci\"]\n");
    assertThat(check.type()).isEqualTo("vulnerabilities");
    assertThat(check.command()).isEmpty();
    assertThat(check.profiles()).containsExactly("ci");
    assertThat(check.vulnerabilities()).isEqualTo(VulnerabilityOptions.defaults());
  }

  @Test
  void aCheckNamedVulnerabilitiesNeedsNoUses() throws IOException {
    assertThat(check("profiles = [\"ci\"]\n").type()).isEqualTo("vulnerabilities");
  }

  @Test
  void readsBothOptions() throws IOException {
    final GateConfiguration check =
        check(
            "uses = \"vulnerabilities\"\nminimum-severity = \"high\"\nmax-age-days = "
                + AGE
                + "\n");
    assertThat(check.vulnerabilities()).isEqualTo(new VulnerabilityOptions("high", AGE));
  }

  @Test
  void rejectsMediumAndNamesTheValidValues() {
    assertThatThrownBy(() -> check("uses = \"vulnerabilities\"\nminimum-severity = \"medium\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("minimum-severity")
        .hasMessageContaining("low, moderate, high and critical");
  }

  @Test
  void rejectsAZeroAge() {
    assertThatThrownBy(() -> check("uses = \"vulnerabilities\"\nmax-age-days = 0\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("max-age-days")
        .hasMessageContaining("positive whole number");
  }

  @Test
  void rejectsTheOptionsOnAnotherCheck() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n\n[checks.tests]\ncommand = \"./mvnw test\"\nmax-age-days = "
                        + AGE
                        + "\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("only supported for the vulnerabilities check");
  }
}
