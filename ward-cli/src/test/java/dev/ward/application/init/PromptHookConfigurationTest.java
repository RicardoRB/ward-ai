package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptHookConfigurationTest {
  private static final String SECRETS = "version = 2\n[checks.secrets]\nuses = \"secrets\"\n";

  @TempDir Path root;

  private final PromptHookConfiguration enabler =
      new PromptHookConfiguration(
          new ProjectDetector(new FileSystemProjectInspection()),
          new FileConfigurationStorage(),
          new OwnedSectionWriter()
      );

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
  }

  private boolean scans() {
    return new TomlConfigurationReader()
        .read(root.resolve("ward.toml"))
        .checks()
        .get("secrets")
        .secrets()
        .scanPrompts();
  }

  @Test
  void anExistingSecretsCheckGetsAMarkedEnabledTableOnce() throws IOException {
    Files.writeString(root.resolve("ward.toml"), SECRETS);

    assertThat(enabler.enable(root)).hasValueSatisfying(
        text -> assertThat(text).contains("Enabled")
    );
    enabler.enable(root);

    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml).startsWith(SECRETS).contains("# ward-owned: prompt-hook v1 begin");
    assertThat(toml.split("\\[checks.secrets.prompts]", -1)).hasSize(2);
    assertThat(scans()).isTrue();
  }

  @Test
  void aTableTheUserWroteIsLeftAlone() throws IOException {
    final String own = SECRETS + "[checks.secrets.prompts]\nenabled = false\n";
    Files.writeString(root.resolve("ward.toml"), own);

    assertThat(enabler.enable(root)).hasValueSatisfying(
        text -> assertThat(text).contains("unchanged")
    );

    assertThat(Files.readString(root.resolve("ward.toml"))).isEqualTo(own);
    assertThat(scans()).isFalse();
  }

  @Test
  void aProjectWithoutASecretsCheckIsToldAndNotChanged() throws IOException {
    final String toml = "version = 2\n[checks.tests]\ncommand = \"mvn test\"\n";
    Files.writeString(root.resolve("ward.toml"), toml);

    assertThat(enabler.enable(root)).hasValueSatisfying(
        text -> assertThat(text).contains("no [checks.secrets]")
    );

    assertThat(Files.readString(root.resolve("ward.toml"))).isEqualTo(toml);
  }
}
