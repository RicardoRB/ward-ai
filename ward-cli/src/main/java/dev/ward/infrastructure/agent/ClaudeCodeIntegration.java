package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

/** Ownership-safe Claude Code project hook integration. */
public final class ClaudeCodeIntegration implements AgentIntegration {
  public static final String SETTINGS_MARKER = "ward-owned: claude-code-edit-write v1";
  public static final String HOOK_MARKER = "ward-owned: edit-write-guard v1";
  private static final String SETTINGS_FILE = ".claude/settings.json";
  private static final String UPDATED = "Updated Ward Claude Code integration.";
  private final ClaudeSettingsDocument settingsDocument;

  private static final String SETTINGS =
      """
            {
              "wardOwnership": "%s",
              "hooks": {
                "PostToolUse": [
                  {
                    "matcher": "^(Edit|Write|MultiEdit)$",
                    "hooks": [
                      {
                        "type": "command",
                        "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write",
                        "args": []
                      }
                    ]
                  }
                ],
                "Stop": [
                  {
                    "hooks": [
                      {
                        "type": "command",
                        "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write",
                        "args": []
                      }
                    ]
                  }
                ]
              }
            }
            """
          .replace("%s", SETTINGS_MARKER);

  private static final String HOOK =
      """
            #!/bin/sh
            # %s
            # Claude Code passes the hook event JSON on stdin. Ward reads it to check the edited
            # file after edits and the project when the turn ends. Hooks run in the session's
            # current directory, so Ward is pointed at the project explicitly.
            exec ward hook claude-code --learn-after 3 --event-stdin -C "${CLAUDE_PROJECT_DIR:-.}"
            """
          .replace("%s", HOOK_MARKER);

  /** Creates the integration with the default JSON codec. */
  public ClaudeCodeIntegration() {
    this(new ForyJsonCodec());
  }

  /** Creates the integration; {@code codec} reads and writes {@code .claude/settings.json}. */
  @Inject
  public ClaudeCodeIntegration(final JsonCodec codec) {
    settingsDocument = new ClaudeSettingsDocument(Objects.requireNonNull(codec));
  }

  @Override
  public String id() {
    return "claude-code";
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return Set.of(
        IntegrationCapability.POST_EDIT,
        IntegrationCapability.TURN_END,
        IntegrationCapability.FEEDBACK);
  }

  @Override
  public boolean installed(final Path installRoot, final String projectPath) {
    final Path root = installRoot.toAbsolutePath().normalize();
    return Files.isRegularFile(root.resolve(SETTINGS_FILE))
        && Files.isRegularFile(root.resolve(new ClaudeCodeFiles(projectPath).hookFile()));
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public boolean installsAtRepositoryRoot() {
    return true;
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final ClaudeCodeFiles files = new ClaudeCodeFiles(request.projectPath());
    final Path settings = root.resolve(SETTINGS_FILE);
    final Path hook = root.resolve(files.hookFile());
    try {
      return request.remove()
          ? remove(settings, hook, files, root)
          : install(settings, hook, files, root);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not update Claude Code integration: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot) {
    return plan(projectRoot, IntegrationRequest.INSTALL);
  }

  /**
   * Mirrors {@link #install}: the same preflight and settings merge, then the old guard removal.
   */
  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path settings = root.resolve(SETTINGS_FILE);
    final ClaudeCodeFiles files;
    try {
      files = new ClaudeCodeFiles(request.projectPath());
    } catch (WardException unsafe) {
      return List.of(
          new PlannedFileChange(
              settings, PlannedFileChange.Action.CONFLICT, List.of(unsafe.getMessage())));
    }
    final Path hookFile = root.resolve(files.hookFile());
    try {
      final PlannedFileChange hook =
          new PlannedFileChange(
              hookFile, IntegrationArtifacts.decide(hookFile, HOOK_MARKER, files.script(HOOK)));
      if (hook.action() == PlannedFileChange.Action.CONFLICT) {
        return List.of(hook);
      }
      final String content;
      try {
        content =
            settingsDocument.merge(
                settings, SETTINGS_MARKER, files.settings(SETTINGS), files.owns());
      } catch (ClaudeSettingsDocument.SettingsConflictException e) {
        return List.of(
            new PlannedFileChange(
                settings, PlannedFileChange.Action.CONFLICT, List.of(e.getMessage())));
      }
      final List<PlannedFileChange> plan = new ArrayList<>();
      plan.add(new PlannedFileChange(settings, ClaudeSettingsDocument.action(settings, content)));
      plan.add(hook);
      plan.addAll(files.legacyGuards(root).plan());
      return plan;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not plan Claude Code integration: " + e.getMessage(), e);
    }
  }

  /**
   * Installs or refreshes every file after a full conflict preflight. A guard script of an earlier
   * version is deleted after the settings that registered it are rewritten.
   */
  private IntegrationResult install(
      final Path settings, final Path hook, final ClaudeCodeFiles files, final Path root)
      throws IOException {
    final IntegrationResult hookConflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (hookConflict != null) {
      return hookConflict;
    }
    final String settingsContent;
    try {
      settingsContent =
          settingsDocument.merge(settings, SETTINGS_MARKER, files.settings(SETTINGS), files.owns());
    } catch (ClaudeSettingsDocument.SettingsConflictException e) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT, List.of(settings.toString()), e.getMessage());
    }
    final List<Map.Entry<Path, IntegrationResult>> results = new ArrayList<>();
    results.add(
        Map.entry(settings, ClaudeSettingsDocument.write(settings, settingsContent, UPDATED)));
    results.add(
        Map.entry(
            hook,
            IntegrationArtifacts.installOrRefresh(hook, HOOK_MARKER, files.script(HOOK), UPDATED)));
    if (Files.exists(hook)) {
      IntegrationArtifacts.makeExecutable(hook);
    }
    for (final Path old : files.legacyGuards(root).delete()) {
      results.add(
          Map.entry(
              old,
              new IntegrationResult(
                  IntegrationResult.Status.REMOVED, List.of(old.toString()), UPDATED)));
    }
    return IntegrationArtifacts.combine(
        results,
        "Ward Claude Code integration is already present.",
        "Created Ward-owned Claude Code edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(
      final Path settings, final Path hook, final ClaudeCodeFiles files, final Path root)
      throws IOException {
    final IntegrationResult conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (conflict != null) {
      return conflict;
    }
    final ClaudeSettingsDocument.SettingsUpdate update;
    try {
      update = settingsDocument.remove(settings, SETTINGS_MARKER, files.owns());
    } catch (ClaudeSettingsDocument.SettingsConflictException e) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT, List.of(settings.toString()), e.getMessage());
    }
    final LegacyFiles guards = files.legacyGuards(root);
    if (update == null && !Files.exists(hook) && guards.owned().isEmpty()) {
      return new IntegrationResult(
          IntegrationResult.Status.NOT_FOUND, List.of(), "No Ward Claude Code integration found.");
    }

    final List<String> removed = new ArrayList<>();
    if (update != null && update.changed()) {
      Files.writeString(settings, update.content());
      removed.add(settings.toString());
    }
    if (Files.exists(hook)) {
      Files.delete(hook);
      removed.add(hook.toString());
    }
    guards.delete().forEach(file -> removed.add(file.toString()));
    EmptyDirectories.delete(hook.getParent());
    EmptyDirectories.delete(settings.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, removed, "Removed Ward-owned Claude Code integration.");
  }
}
