package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** A {@code uses} key naming the removed {@code semgrep} type is a configuration error. */
class TomlRemovedSemgrepTest {
  @Test
  void usesSemgrepIsAConfigurationError() {
    assertThatThrownBy(
            () ->
                new TomlConfigurationReader()
                    .parse(
                        Toml.parse(
                            "version = 2\n[checks.x]\nuses = \"semgrep\"\ncommand = \"tool\"\n")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.x.uses");
  }
}
