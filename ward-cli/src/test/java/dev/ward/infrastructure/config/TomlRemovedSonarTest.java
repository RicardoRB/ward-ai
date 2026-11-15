package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** A {@code uses} key naming the removed {@code sonar} type is a configuration error. */
class TomlRemovedSonarTest {
  @Test
  void usesSonarIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                new TomlConfigurationReader()
                    .parse(
                        Toml.parse(
                            "version = 2\n[checks.x]\nuses = \"sonar\"\ncommand = \"tool\"\n")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.x.uses");
  }
}
