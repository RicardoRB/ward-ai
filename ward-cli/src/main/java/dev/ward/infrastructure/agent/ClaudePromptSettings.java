package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationRequest;

/** The Claude Code settings template with the {@code UserPromptSubmit} hook, when asked for. */
final class ClaudePromptSettings {
  private static final String END = "\n    ]\n  }\n}\n";
  private static final String PROMPT_EVENT =
      """
      ,
          "UserPromptSubmit": [
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
      """;

  private ClaudePromptSettings() {}

  /** Returns {@code settings}, with the prompt event registered when the request asks for it. */
  static String template(final String settings, final IntegrationRequest request) {
    if (!request.promptHook()) {
      return settings;
    }
    final int end = settings.lastIndexOf(END);
    return settings.substring(0, end)
        + "\n    ]"
        + PROMPT_EVENT.stripTrailing()
        + settings.substring(end + "\n    ]".length());
  }
}
