package dev.ward.domain.agent;

import java.util.List;
import java.util.stream.Collectors;

/** Canonical agent integration identifiers and display names. */
public final class SupportedAgents {
  /** The agents with a hook event that fires on a prompt and can reject it. */
  private static final List<String> PROMPT_HOOK_IDS =
      List.of("claude-code", "codex", "cursor", "gemini", "factory-droid");

  private static final List<Definition> DEFINITIONS =
      List.of(
          new Definition("opencode", "OpenCode"),
          new Definition("claude-code", "Claude Code"),
          new Definition("codex", "OpenAI Codex CLI"),
          new Definition("cursor", "Cursor"),
          new Definition("copilot", "GitHub Copilot CLI"),
          new Definition("gemini", "Gemini CLI"),
          new Definition("windsurf", "Windsurf"),
          new Definition("cline", "Cline"),
          new Definition("factory-droid", "Factory Droid"),
          new Definition("amp", "Amp"),
          new Definition("junie", "Junie CLI")
      );

  /**
   * One supported agent.
   *
   * @param id the ID used on the command line
   * @param label the name shown to users
   */
  public record Definition(String id, String label) {}

  private SupportedAgents() {}

  /** Returns the agents in {@code ids} that have no prompt hook, in the order given. */
  public static List<String> withoutPromptHook(final List<String> ids) {
    return ids.stream()
        .filter(id -> ids().contains(id) && !PROMPT_HOOK_IDS.contains(id))
        .toList();
  }

  /** Returns the agents that have a prompt hook. */
  public static List<String> promptHookIds() {
    return PROMPT_HOOK_IDS;
  }

  /** Returns every supported agent, in menu order. */
  public static List<Definition> definitions() {
    return DEFINITIONS;
  }

  /** Returns the supported agent IDs, in menu order. */
  public static List<String> ids() {
    return DEFINITIONS.stream().map(Definition::id).toList();
  }

  /** Returns the supported agent IDs as a comma-separated list for messages. */
  public static String idsText() {
    return DEFINITIONS.stream().map(Definition::id).collect(Collectors.joining(", "));
  }
}
