package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeAgentIntegrationTest {
  @Test
  void installsRefreshesAndRemovesOwnedArtifacts(@TempDir final Path root) throws Exception {
    final CodexIntegration integration = new CodexIntegration();

    final IntegrationResult created = integration.integrate(root, IntegrationRequest.INSTALL);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(created.capabilities())
        .containsExactlyInAnyOrder(
            IntegrationCapability.POST_EDIT,
            IntegrationCapability.TURN_END,
            IntegrationCapability.FEEDBACK);
    assertThat(Files.readString(root.resolve(".codex/hooks.json")))
        .contains("ward-owned: codex-native-hooks v1");
    assertThat(Files.readString(root.resolve(".codex/hooks/ward.sh")))
        .contains("ward hook native --agent codex");

    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(integration.installed(root)).isFalse();
  }

  @Test
  void preservesUserOwnedTargetsAsConflicts(@TempDir final Path root) throws Exception {
    final Path config = root.resolve(".codex/hooks.json");
    Files.createDirectories(config.getParent());
    Files.writeString(config, "user configuration");

    final CodexIntegration integration = new CodexIntegration();
    final IntegrationResult result = integration.integrate(root, IntegrationRequest.INSTALL);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(Files.readString(config)).isEqualTo("user configuration");
    assertThat(root.resolve(".codex/hooks/ward.sh")).doesNotExist();
    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.CONFLICT);
  }

  @Test
  void reportsPartialInstallation(@TempDir final Path root) throws Exception {
    final CodexIntegration integration = new CodexIntegration();
    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.delete(root.resolve(".codex/hooks/ward.sh"));

    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.PARTIAL);
  }

  @Test
  void clineRefreshRemovesTheOwnedPreToolUseFileOfAnEarlierVersion(@TempDir final Path root)
      throws Exception {
    final ClineIntegration integration = new ClineIntegration();
    final Path preTool = root.resolve(".cline/hooks/PreToolUse");
    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.writeString(preTool, "#!/bin/sh\n# ward-owned: cline-native-hooks v1\nexit 0\n");

    final IntegrationResult refreshed = integration.integrate(root, IntegrationRequest.INSTALL);

    assertThat(refreshed.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(refreshed.changed()).containsExactly(preTool.toString());
    assertThat(preTool).doesNotExist();
    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void clineRemovalDeletesTheOwnedPreToolUseFileAndKeepsAUserOwnedOne(@TempDir final Path root)
      throws Exception {
    final ClineIntegration integration = new ClineIntegration();
    final Path preTool = root.resolve(".cline/hooks/PreToolUse");
    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.writeString(preTool, "#!/bin/sh\n# ward-owned: cline-native-hooks v1\nexit 0\n");
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).changed())
        .contains(preTool.toString());
    assertThat(preTool).doesNotExist();

    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.writeString(preTool, "#!/bin/sh\necho mine\n");
    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(preTool).hasContent("#!/bin/sh\necho mine\n");
    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.MISSING);
  }
}
