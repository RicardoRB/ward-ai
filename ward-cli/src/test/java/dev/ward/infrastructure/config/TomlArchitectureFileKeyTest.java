package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** The {@code architecture-file} key of a {@code ward} check, and the rules that stay out of it. */
class TomlArchitectureFileKeyTest {

  private static GateConfiguration check(final String extra) {
    return new TomlConfigurationReader()
        .parse(Toml.parse("version = 2\n[checks.ward]\nuses = \"ward\"\n" + extra + "\n"))
        .checks()
        .get("ward");
  }

  @Test
  void readsARelativePath() {
    assertThat(check("architecture-file = \"config/architecture.toml\"").architectureFile())
        .isEqualTo("config/architecture.toml");
  }

  @Test
  void aCheckWithoutTheKeyUsesTheDefaultFile() {
    assertThat(check("").architectureFile()).isNull();
  }

  @Test
  void rejectsPathsOutsideTheProject() {
    for (final String value :
        new String[] {"\"/etc/x.toml\"", "\"../shared/x.toml\"", "\"\"", "3"}) {
      assertThatThrownBy(() -> check("architecture-file = " + value))
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.ward.architecture-file");
    }
  }

  @Test
  void generalRulesAreNotAcceptedInWardToml() {
    assertThatThrownBy(() -> check("architecture = [{ name = \"x\" }]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.architecture")
        .hasMessageContaining("Supported keys");
  }
}
