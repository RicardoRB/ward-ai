package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlPromptsTableTest {
  @TempDir Path dir;
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @Test
  @DisplayName("prompt scanning is configured in its own table")
  void promptScanningIsConfiguredInItsOwnTable() throws IOException {
    // Given ward.toml with [checks.secrets.prompts] enabled, and one with it absent or disabled
    final String enabled = "version = 2\n[checks.secrets]\nuses = \"secrets\"\n"
        + "[checks.secrets.prompts]\nenabled = true\n";
    final String disabled = enabled.replace("true", "false");
    final String absent = "version = 2\n[checks.secrets]\nuses = \"secrets\"\n";

    // When Ward loads the configuration
    final boolean on = read(reader, dir, enabled).checks().get("secrets").secrets().scanPrompts();
    final boolean off = read(reader, dir, disabled).checks().get("secrets").secrets().scanPrompts();
    final boolean none = read(reader, dir, absent).checks().get("secrets").secrets().scanPrompts();

    // Then only the enabled table makes the check scan prompts
    assertThat(on).isTrue();
    assertThat(off).isFalse();
    assertThat(none).isFalse();
  }

  @Test
  @DisplayName("a prompts table on a check without prompt support")
  void aPromptsTableOnACheckWithoutPromptSupport() {
    // Given ward.toml with a prompts table under a check that does not support prompts
    final String toml = "version = 2\n[checks.tests]\ncommand = \"./mvnw test\"\n"
        + "[checks.tests.prompts]\nenabled = true\n";

    // When Ward loads the configuration
    // Then it fails with a message that names the check and says it does not scan prompts
    assertThatThrownBy(() -> read(reader, dir, toml))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests")
        .hasMessageContaining("does not scan prompts");
  }

  @Test
  void anUnknownKeyOrWrongTypeInThePromptsTableFails() {
    final String base = "version = 2\n[checks.secrets]\nuses = \"secrets\"\n[checks.secrets.prompts]\n";

    assertThatThrownBy(() -> read(reader, dir, base + "enabled = \"yes\"\n"))
        .hasMessageContaining("must be true or false");
    assertThatThrownBy(() -> read(reader, dir, base + "mode = 1\n"))
        .hasMessageContaining("not a key of a prompts table");
    assertThatThrownBy(
        () -> read(reader, dir, "version = 2\n[checks.secrets]\nuses = \"secrets\"\nprompts = 1\n")
    )
        .hasMessageContaining("must be a table");
  }
}
