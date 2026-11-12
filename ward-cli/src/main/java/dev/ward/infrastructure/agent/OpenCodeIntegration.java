package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- ward-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "ward-owned: opencode-edit-write v1";

  private static final String COMMAND =
      MARKER + "\n\nRun `ward check --format json --learn-after 3` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            import { Plugin } from "@opencode/plugin";

            // Native OpenCode V2 quality plugin (@opencode/plugin 2.0.30).
            // Hook: tool "execute.after". The host treats an
            // execute.after rejection as a defect, so feedback is appended to the content the model
            // reads instead of being thrown.
            const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "apply_patch", "patch"]);
            const CHECK = ["check", "--profile", "edit", "--format", "json", "--learn-after", "3"];
            const MAX_FEEDBACK = 20000;
            const bound = (text) => (text.length > MAX_FEEDBACK ? `${text.slice(0, MAX_FEEDBACK)}\\n[truncated]` : text);
            const diagnostic = (message) => console.warn(`[ward] ${String(message).slice(0, 500)}`);
            const editedPaths = (input, result) => {
              const args = input && typeof input === "object" ? input : {};
              const files = Array.isArray(result?.metadata?.files) ? result.metadata.files : [];
              const paths = [args.filePath, args.path, ...files.flatMap((file) => [file?.filePath, file?.movePath])];
              return [...new Set(paths.filter((path) => typeof path === "string" && path))];
            };
            // Mirrors the host's content normalization so appended feedback keeps the original result.
            const contentOf = (result) => {
              const content = result?.content;
              if (typeof content === "string") return [{ type: "text", text: content }];
              if (Array.isArray(content) && content.length > 0) return [...content];
              const output = result?.output;
              return [{ type: "text", text: typeof output === "string" ? output : (JSON.stringify(output) ?? String(output)) }];
            };

            export default Plugin.define({
              id: "ward.edit-write",
              setup: async (ctx) => {
                const abort = new AbortController();
                const run = async (args, directory) => {
                  try {
                    const proc = Bun.spawn(["ward", ...args], { cwd: directory, stdout: "pipe", stderr: "pipe", signal: abort.signal });
                    const [stdout, stderr, code] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text(), proc.exited]);
                    return { code, stdout, stderr };
                  } catch (error) {
                    return { code: null, stdout: "", stderr: "", error: String(error?.message ?? error).slice(0, 500) };
                  }
                };
                // The session's current location is authoritative; a failed lookup skips validation
                // rather than guessing a directory.
                const locationFor = async (sessionID) => {
                  try {
                    const directory = (await ctx.session.get({ sessionID }))?.location?.directory;
                    if (typeof directory === "string" && directory) return directory;
                    diagnostic("session has no location; skipping validation");
                  } catch (error) {
                    diagnostic(`session location unavailable: ${error?.message ?? error}`);
                  }
                  return null;
                };
                const validateEdit = async (event, directory) => {
                  const paths = editedPaths(event.input, event.result);
                  const checkArgs = paths.flatMap((path) => ["--path", path]);
                  const check = await run([...CHECK, ...checkArgs], directory);
                  if (check.error) return `Ward could not run the quality check: ${check.error}`;
                  let report = null;
                  try { report = JSON.parse(check.stdout); } catch { /* Non-JSON output is reported as text. */ }
                  const learning = (report?.learning?.prompts ?? [])
                    .map((prompt) => prompt?.instruction)
                    .filter((instruction) => typeof instruction === "string" && instruction);
                  const parts = [];
                  if (check.code !== 0) parts.push(`Ward quality check failed:\\n${bound((check.stdout || check.stderr).trim())}`);
                  return parts.concat(learning).join("\\n");
                };
                const after = await ctx.tool.hook("execute.after", async (event) => {
                  if (abort.signal.aborted || event.status !== "completed" || !EDIT_TOOLS.has(String(event.tool).toLowerCase())) return;
                  try {
                    const directory = await locationFor(event.sessionID);
                    if (!directory) return;
                    const feedback = await validateEdit(event, directory);
                    if (feedback && !abort.signal.aborted) {
                      event.result = { ...event.result, content: [...contentOf(event.result), { type: "text", text: feedback }] };
                    }
                  } catch (error) {
                    diagnostic(`post-edit validation error: ${error?.message ?? error}`);
                  }
                });
                return async () => {
                  abort.abort();
                  await after.dispose();
                };
              },
            });
            """
          .replace("%s", PLUGIN_MARKER);

  @Override
  public String id() {
    return "opencode";
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK);
  }

  @Override
  public boolean installed(final Path installRoot, final String projectPath) {
    final Path root = installRoot.toAbsolutePath().normalize();
    final OpenCodeFiles files = new OpenCodeFiles(projectPath);
    return Files.isRegularFile(root.resolve(files.commandFile()))
        && Files.isRegularFile(root.resolve(files.pluginFile()));
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
    final OpenCodeFiles files = new OpenCodeFiles(request.projectPath());
    final Path command = root.resolve(files.commandFile());
    final Path plugin = root.resolve(files.pluginFile());
    try {
      return request.remove()
          ? remove(command, plugin, files.legacyGuards(root))
          : install(command, plugin, files, root);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot) {
    return plan(projectRoot, IntegrationRequest.INSTALL);
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final OpenCodeFiles files = new OpenCodeFiles(request.projectPath());
    try {
      final List<PlannedFileChange> plan = new ArrayList<>();
      final Path command = root.resolve(files.commandFile());
      final Path plugin = root.resolve(files.pluginFile());
      plan.add(
          new PlannedFileChange(
              command, IntegrationArtifacts.decide(command, MARKER, files.command(COMMAND))));
      plan.add(
          new PlannedFileChange(
              plugin, IntegrationArtifacts.decide(plugin, PLUGIN_MARKER, files.plugin(PLUGIN))));
      final List<PlannedFileChange> conflicts =
          plan.stream()
              .filter(change -> change.action() == PlannedFileChange.Action.CONFLICT)
              .toList();
      if (!conflicts.isEmpty()) {
        // Mirrors install: a conflict stops the run before anything is written.
        return conflicts;
      }
      plan.addAll(files.legacyGuards(root).plan());
      return plan;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not plan OpenCode integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(
      final Path command, final Path plugin, final OpenCodeFiles files, final Path root)
      throws IOException {
    final IntegrationResult conflict =
        IntegrationArtifacts.preflight(command, MARKER, plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }
    final IntegrationResult commandResult =
        IntegrationArtifacts.installOrRefresh(
            command, MARKER, files.command(COMMAND), "Updated Ward OpenCode integration.");
    if (commandResult.status() == IntegrationResult.Status.CONFLICT) {
      return commandResult;
    }
    final IntegrationResult pluginResult =
        IntegrationArtifacts.installOrRefresh(
            plugin, PLUGIN_MARKER, files.plugin(PLUGIN), "Updated Ward OpenCode integration.");
    if (pluginResult.status() == IntegrationResult.Status.CONFLICT) {
      return pluginResult;
    }
    final List<Map.Entry<Path, IntegrationResult>> results =
        new ArrayList<>(
            List.of(Map.entry(command, commandResult), Map.entry(plugin, pluginResult)));
    for (final Path old : files.legacyGuards(root).delete()) {
      results.add(
          Map.entry(
              old,
              new IntegrationResult(
                  IntegrationResult.Status.REMOVED,
                  List.of(old.toString()),
                  "Removed the Ward-owned guard plugin of an earlier version.")));
    }
    return IntegrationArtifacts.combine(
        results,
        "Ward OpenCode integration is already present.",
        "Created Ward-owned OpenCode command and edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(final Path command, final Path plugin, final LegacyFiles guards)
      throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(command, MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }
    if (!Files.exists(command) && !Files.exists(plugin) && guards.owned().isEmpty()) {
      return new IntegrationResult(
          IntegrationResult.Status.NOT_FOUND, List.of(), "No Ward OpenCode integration found.");
    }

    final List<String> removed = new ArrayList<>();
    if (Files.exists(command)) {
      Files.delete(command);
      removed.add(command.toString());
    }
    if (Files.exists(plugin)) {
      Files.delete(plugin);
      removed.add(plugin.toString());
    }
    guards.delete().forEach(file -> removed.add(file.toString()));
    EmptyDirectories.delete(plugin.getParent());
    EmptyDirectories.delete(command.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, removed, "Removed Ward-owned OpenCode integration.");
  }
}
