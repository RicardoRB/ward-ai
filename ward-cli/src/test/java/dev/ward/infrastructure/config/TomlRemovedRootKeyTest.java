package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

class TomlRemovedRootKeyTest {
  @Test
  void aLeftoverRootKeyOnACheckIsRejectedWithTheKeyName() {
    final String config =
        "version = 2\n[checks.tests]\ncommand = \"mvn test\"\nroot = \"openspec\"\n";

    assertThatThrownBy(() -> new TomlConfigurationReader().parse(Toml.parse(config)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.tests.root")
        .hasMessageContaining("no longer supported");
  }
}
