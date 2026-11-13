package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An earlier Ward version registered an opt-in guard; refresh and removal clean it up. */
class ClaudeCodeLegacyGuardTest {
  private static final String GUARD_COMMAND =
      "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-openspec-guard";
  private static final String LEGACY_SETTINGS =
      """
      {
        "wardOwnership": "ward-owned: claude-code-edit-write v1",
        "hooks": {
          "PreToolUse": [{"matcher": "^(Edit|Write)$", "hooks": [{"type": "command", "command": "%s", "args": []}]}],
          "PostToolUse": [{"matcher": "^(Edit|Write|MultiEdit)$", "hooks": [{"type": "command", "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write", "args": []}]}],
          "Stop": [{"hooks": [{"type": "command", "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write", "args": []}]}]
        }
      }
      """
          .formatted(GUARD_COMMAND);
  private static final String LEGACY_GUARD =
      "#!/bin/sh\n# ward-owned: openspec-guard v1\nexec ward hook claude-code --guard-approved-specs\n";

  private final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

  @TempDir Path root;

  private Path settings() {
    return root.resolve(".claude/settings.json");
  }

  private Path guard() {
    return root.resolve(".claude/hooks/ward-openspec-guard");
  }

  private void installLegacy(final String guardContent) throws IOException {
    integration.integrate(root, false);
    Files.writeString(settings(), LEGACY_SETTINGS);
    Files.writeString(guard(), guardContent);
  }

  private boolean hasPreToolUse() throws IOException {
    return JsonTree.parse(Files.readString(settings())).get("hooks").has("PreToolUse");
  }

  @Test
  void refreshRemovesTheOwnedGuardAndItsRegistration() throws IOException {
    installLegacy(LEGACY_GUARD);

    final IntegrationResult result = integration.integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(guard().toString(), settings().toString());
    assertThat(guard()).doesNotExist();
    assertThat(hasPreToolUse()).isFalse();
    assertThat(root.resolve(".claude/hooks/ward-edit-write")).exists();
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void planNamesTheGuardRemoval() throws IOException {
    installLegacy(LEGACY_GUARD);

    assertThat(integration.plan(root))
        .anySatisfy(
            change -> {
              assertThat(change.file()).isEqualTo(guard());
              assertThat(change.action())
                  .isEqualTo(dev.ward.domain.agent.PlannedFileChange.Action.REMOVE);
            });
  }

  @Test
  void userOwnedFileAtTheGuardPathIsPreserved() throws IOException {
    installLegacy("#!/bin/sh\necho mine\n");

    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(hasPreToolUse()).isFalse();
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(guard()).hasContent("#!/bin/sh\necho mine\n");
  }

  @Test
  void removalDeletesTheOwnedGuardWithTheIntegration() throws IOException {
    installLegacy(LEGACY_GUARD);

    final IntegrationResult result = integration.integrate(root, IntegrationRequest.REMOVE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(result.changed()).contains(guard().toString());
    assertThat(guard()).doesNotExist();
  }

  @Test
  void ownedGuardAloneIsStillFoundForRemoval() throws IOException {
    installLegacy(LEGACY_GUARD);
    Files.delete(settings());
    Files.delete(root.resolve(".claude/hooks/ward-edit-write"));

    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);
  }
}
