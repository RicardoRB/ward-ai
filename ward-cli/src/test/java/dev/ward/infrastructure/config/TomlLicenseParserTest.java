package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TomlLicenseParserTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  private LicenseSettings settings(final String body) throws IOException {
    return read(reader, dir, "version = 2\n\n[checks.license]\nuses = \"license\"\n" + body)
        .checks()
        .get("license")
        .license();
  }

  @Test
  void aLicenseCheckWithoutACommandGetsTheDefaultPolicy() throws IOException {
    final LicenseSettings settings = settings("");

    assertThat(settings).isEqualTo(LicenseSettings.defaults());
  }

  @Test
  void readsEveryKey() throws IOException {
    final LicenseSettings settings =
        settings(
            "deny = [\"GPL-*\"]\nexceptions = []\nscopes = [\"runtime\", \"provided\"]\n"
                + "unknown = \"warn\"\n");

    assertThat(settings.mode()).isEqualTo(LicenseSettings.Mode.DENY);
    assertThat(settings.patterns()).containsExactly("GPL-*");
    assertThat(settings.exceptions()).isEmpty();
    assertThat(settings.scopes()).containsExactlyInAnyOrder("runtime", "provided");
    assertThat(settings.unknownFails()).isFalse();
  }

  @Test
  void readsAnAllowList() throws IOException {
    final LicenseSettings settings = settings("allow = [\"Apache-2.0\", \"MIT\"]\n");

    assertThat(settings.mode()).isEqualTo(LicenseSettings.Mode.ALLOW);
    assertThat(settings.patterns()).isEqualTo(List.of("Apache-2.0", "MIT"));
    assertThat(settings.scopes()).isEqualTo(Set.of());
  }

  @Test
  void otherChecksHaveNoLicenseSettings() throws IOException {
    final WardConfiguration config =
        read(reader, dir, "version = 2\n[checks.t]\ncommand = \"./mvnw test\"\n");

    assertThat(config.checks().get("t").license()).isNull();
  }

  @Test
  void aCommandOnALicenseCheckIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.license]\nuses = \"license\"\n"
                        + "command = [\"./mvnw\", \"license:add-third-party\"]\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContainingAll("checks.license.command", "native", "no command");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "allow = [\"MIT\"]; deny = [\"GPL-*\"]; |checks.license.allow|checks.license.deny",
        "deny = \"GPL-*\"; |checks.license.deny|array",
        "allow = [\"*MIT\"]; |checks.license.allow|SPDX",
        "allow = [1]; |checks.license.allow|SPDX",
        "exceptions = [\"a b\"]; |checks.license.exceptions|SPDX",
        "scopes = [\"compile\"]; |checks.license.scopes|runtime",
        "scopes = \"runtime\"; |checks.license.scopes|array",
        "scopes = [3]; |checks.license.scopes|runtime",
        "unknown = \"ignore\"; |checks.license.unknown|warn",
        "unknown = true; |checks.license.unknown|warn"
      })
  void rejectsInvalidValuesAndNamesTheKey(final String body, final String key, final String hint) {
    assertThatThrownBy(() -> settings(body.replace(';', '\n')))
        .isInstanceOf(WardException.class)
        .hasMessageContainingAll(key, hint);
  }
}
