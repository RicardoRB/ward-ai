package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

class TomlRulesFileKeyTest {

  private static String config(final String value) {
    return "version = 2\n[checks.ward]\nuses = \"ward\"\nrules-file = " + value + "\n";
  }

  private static String rulesFile(final String value) {
    return new TomlConfigurationReader()
        .parse(Toml.parse(config(value)))
        .checks()
        .get("ward")
        .rulesFile();
  }

  @Test
  void readsARelativePath() {
    assertThat(rulesFile("\"config/rules.toml\"")).isEqualTo("config/rules.toml");
  }

  @Test
  void rejectsPathsOutsideTheProject() {
    for (final String value : new String[] {"\"/etc/x.toml\"", "\"../x.toml\"", "\"\"", "3"}) {
      assertThatThrownBy(() -> rulesFile(value))
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.ward.rules-file");
    }
  }
}
