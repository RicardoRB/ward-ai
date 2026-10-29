package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Configuration syntax cases share one reader fixture and are kept as one contract suite.
class TomlValidationTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void rejectsInvalidToml() {
    assertThatThrownBy(() -> read(reader, dir, "version = = 1"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Invalid TOML");
  }

  @Test
  void rejectsMissingOrUnsupportedVersion() {
    assertThatThrownBy(() -> read(reader, dir, "[checks.tests]\ncommand = \"x\""))
        .hasMessageContaining("version");
    assertThatThrownBy(() -> read(reader, dir, "version = 3")).hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> read(reader, dir, "version = \"1\"")).hasMessageContaining("integer");
  }

  @Test
  void rejectsEnabledGateWithoutCommand() {
    assertThatThrownBy(() -> read(reader, dir, "version = 2\n[checks.tests]\nenabled = true"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.tests.command");
  }

  @Test
  void rejectsBadCommandTypes() {
    assertThatThrownBy(() -> read(reader, dir, "version = 2\n[checks.tests]\ncommand = 5"))
        .hasMessageContaining("string or an array");
    assertThatThrownBy(() -> read(reader, dir, "version = 2\n[checks.tests]\ncommand = [\"a\", 1]"))
        .hasMessageContaining("only strings");
    assertThatThrownBy(() -> read(reader, dir, "version = 2\n[checks.tests]\ncommand = \"  \""))
        .hasMessageContaining("empty");
    assertThatThrownBy(
            () ->
                read(
                    reader, dir, "version = 2\n[checks.tests]\nenabled = \"yes\"\ncommand = \"x\""))
        .hasMessageContaining("true or false");
  }

  @Test
  void parsesGateProfilesAndRejectsInvalidOrLegacyProfiles() throws Exception {
    final WardConfiguration defaults = read(reader, dir, "version = 2\n[checks.a]\ncommand = 'x'");
    assertThat(defaults.checks().get("a").profiles()).containsExactly("default");
    final WardConfiguration configured =
        read(
            reader,
            dir,
            "version = 2\n[checks.a]\ncommand = 'x'\nprofiles = [' ci ', 'fast']\nenabled = false");
    assertThat(configured.checks().get("a").profiles()).containsExactly("ci", "fast");
    for (final String value : List.of("[]", "[1]", "['  ']", "'not-an-array'")) {
      assertThatThrownBy(
              () ->
                  read(
                      reader,
                      dir,
                      "version = 2\n[checks.a]\ncommand = 'x'\nprofiles = "
                          + value
                          + "\nenabled = false"))
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.a.profiles");
    }
    assertThatThrownBy(() -> read(reader, dir, "version = 2\n[profiles.old]\ngates = ['a']"))
        .hasMessageContaining("no longer supported")
        .hasMessageContaining("[checks.<name>]");
  }
}
