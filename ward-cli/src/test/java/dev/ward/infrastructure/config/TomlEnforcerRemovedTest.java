package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** The {@code enforcer} check type is gone; a command that runs the plugin is a plain command. */
class TomlEnforcerRemovedTest {
  private static GateConfiguration check(final String body) {
    return new TomlConfigurationReader()
        .parse(Toml.parse("version = 2\n[checks.rules]\n" + body))
        .checks()
        .get("rules");
  }

  @Test
  void theEnforcerTypeIsRejectedAsUnknown() {
    assertThatThrownBy(() -> check("uses = \"enforcer\"\ncommand = \"./mvnw enforcer:enforce\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.rules.uses");
  }

  @Test
  void aCommandThatRunsTheEnforcerPluginIsAPlainCommandCheck() {
    assertThat(check("command = \"./mvnw enforcer:enforce\"\n").type()).isEqualTo("command");
  }
}
