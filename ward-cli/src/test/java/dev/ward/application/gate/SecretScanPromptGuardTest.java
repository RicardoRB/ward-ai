package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FixedProjectInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.secrets.PromptInput;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretScanPromptGuardTest extends SecretGateTestSupport {
  private static final String HEAD = "version = 2\n[checks.secrets]\nuses = \"secrets\"\n";

  private PromptGuard guard() {
    final SecretGates gates =
        new SecretGates(ports(), (projectRoot, since, sink) -> sink.accept(List.of()));
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    return new PromptGuard(
        new ProjectDetector(new FixedProjectInspection(project)),
        new TomlConfigurationReader(),
        gates
    );
  }

  private String secret() throws IOException {
    try (InputStream stream =
        getClass().getResourceAsStream("/rules/secrets/github-pat/violating.txt")) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
  }

  @Test
  @DisplayName("prompt scanning is configured in its own table")
  void promptScanningIsConfiguredInItsOwnTable() throws IOException {
    // Given ward.toml with [checks.secrets.prompts] enabled
    write("ward.toml", HEAD + "[checks.secrets.prompts]\nenabled = true\n");
    final PromptInput prompt = new PromptInput("token " + secret());

    // When I submit a prompt with a fake secret
    final boolean blocked = !guard().scan(root, prompt).isEmpty();

    // Then it is blocked, and with the table absent or disabled the prompt is not scanned
    assertThat(blocked).isTrue();
    write("ward.toml", HEAD + "[checks.secrets.prompts]\nenabled = false\n");
    assertThat(guard().scan(root, prompt)).isEmpty();
    write("ward.toml", HEAD);
    assertThat(guard().scan(root, prompt)).isEmpty();
  }
}
