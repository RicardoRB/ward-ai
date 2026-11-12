package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenCodeIntegrationTest {
  private static final String COMMAND_PATH = ".opencode/commands/ward-check.md";
  private static final String PLUGIN_PATH = ".opencode/plugins/ward-edit-write.js";
  private static final String GUARD_PATH = ".opencode/plugins/ward-openspec-guard.js";
  private static final String LEGACY_GUARD =
      "// ward-owned: opencode-openspec-guard v1\nexport default {};\n";

  @Test
  void integratesAndRemovesOnlyOwnedContent() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    final IntegrationResult created = integration.integrate(root, false);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.exists(root.resolve(COMMAND_PATH))).isTrue();
    assertThat(Files.readString(root.resolve(PLUGIN_PATH)))
        .contains(
            OpenCodeIntegration.PLUGIN_MARKER,
            "import { Plugin } from \"@opencode/plugin\"",
            "export default Plugin.define({",
            "id: \"ward.edit-write\"",
            "ctx.tool.hook(\"execute.after\"",
            "ward",
            "--learn-after",
            "multiedit",
            "patch");
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(root.resolve(PLUGIN_PATH)).doesNotExist();
    assertThat(root.resolve(COMMAND_PATH)).doesNotExist();
  }

  @Test
  void refusesToOverwriteUnownedCommand() throws Exception {
    final Path target = Files.createTempDirectory("ward-opencode").resolve(COMMAND_PATH);
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned");
    assertThat(
            new OpenCodeIntegration()
                .integrate(target.getParent().getParent().getParent(), false)
                .status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
  }

  @Test
  void refusesToInstallPluginWhenItConflictsWithoutTouchingCommand() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-plugin-conflict");
    final Path plugin = root.resolve(PLUGIN_PATH);
    Files.createDirectories(plugin.getParent());
    Files.writeString(plugin, "user-owned");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, false);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(root.resolve(COMMAND_PATH)).doesNotExist();
    assertThat(plugin).hasContent("user-owned");
  }

  @Test
  void pluginChecksEditedPathsAndAppendsFeedbackWithoutSpecValidationOrIdleHooks()
      throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-check");
    new OpenCodeIntegration().integrate(root, false);

    final String plugin = Files.readString(root.resolve(PLUGIN_PATH));

    assertThat(plugin)
        .contains(
            "new Set([\"edit\", \"write\", \"multiedit\", \"apply_patch\", \"patch\"])",
            "args.filePath",
            "args.path",
            "result?.metadata?.files",
            "file?.filePath",
            "file?.movePath",
            "const checkArgs = paths.flatMap((path) => [\"--path\", path])",
            "[\"check\", \"--profile\", \"edit\", \"--format\", \"json\", \"--learn-after\", \"3\"]",
            "content: [...contentOf(event.result), { type: \"text\", text: feedback }]",
            "after.dispose")
        .doesNotContain("spec", "session.idle", "ctx.event", "ctx.session.prompt");
  }

  @Test
  void refreshesPreviousOwnedPluginAndKeepsItsMarker() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-refresh");
    final Path plugin = root.resolve(PLUGIN_PATH);
    Files.createDirectories(plugin.getParent());
    Files.writeString(
        plugin,
        "// "
            + OpenCodeIntegration.PLUGIN_MARKER
            + "\n// OpenCode post-tool hook for Edit and Write operations.\n"
            + "export default async () => ({});\n");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(plugin.toString());
    assertThat(Files.readString(plugin))
        .startsWith("// " + OpenCodeIntegration.PLUGIN_MARKER)
        .doesNotContain("session.idle");
  }

  @Test
  void refreshRemovesTheGuardPluginOfAnEarlierVersion() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-guard");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    integration.integrate(root, false);
    final Path guard = root.resolve(GUARD_PATH);
    Files.writeString(guard, LEGACY_GUARD);

    final IntegrationResult refreshed = integration.integrate(root, false);

    assertThat(refreshed.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(refreshed.changed()).containsExactly(guard.toString());
    assertThat(guard).doesNotExist();
    assertThat(root.resolve(PLUGIN_PATH)).exists();
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void removalDeletesTheGuardPluginOfAnEarlierVersion() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-guard-remove");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    integration.integrate(root, false);
    final Path guard = root.resolve(GUARD_PATH);
    Files.writeString(guard, LEGACY_GUARD);

    final IntegrationResult removed = integration.integrate(root, true);

    assertThat(removed.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(removed.changed()).contains(guard.toString());
    assertThat(guard).doesNotExist();
  }

  @Test
  void userOwnedFileAtTheGuardPathIsPreserved() throws Exception {
    final Path root = Files.createTempDirectory("ward-opencode-guard-conflict");
    final Path guard = root.resolve(GUARD_PATH);
    Files.createDirectories(guard.getParent());
    Files.writeString(guard, "user-owned");
    final OpenCodeIntegration integration = new OpenCodeIntegration();

    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(guard).hasContent("user-owned");
  }
}
