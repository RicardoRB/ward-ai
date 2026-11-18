package dev.ward.infrastructure.agent;

import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.secrets.PromptInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * The prompt event of each native harness that has one, and how it rejects a prompt, read from the
 * vendors' hook documentation. A harness without an entry has no prompt hook.
 */
public final class NativePromptEvents {
  private static final String PROMPT_SUBMIT = "UserPromptSubmit";
  private static final String BLOCK = "{\"decision\":\"block\",\"reason\":";
  private static final Map<String, Set<String>> EVENTS =
      Map.of(
          "codex",
          Set.of(PROMPT_SUBMIT),
          "factory-droid",
          Set.of(PROMPT_SUBMIT),
          "gemini",
          Set.of("BeforeAgent"),
          "cursor",
          Set.of("beforeSubmitPrompt")
      );

  private NativePromptEvents() {}

  /**
   * Returns the prompt of a payload whose event is the prompt event of {@code agent}, with the
   * files it attaches; empty for another event, a payload without a prompt, or an agent without one.
   */
  static Optional<PromptInput> parse(
      final String agent,
      final JsonCodec codec,
      final String payload
  ) {
    final PromptPayload value = codec.fromJson(payload, PromptPayload.class);
    if (value == null
        || value.prompt == null
        || !EVENTS.getOrDefault(agent, Set.of()).contains(value.hookEventName)) {
      return Optional.empty();
    }
    final List<String> files =
        value.attachments == null
            ? List.of()
            : value.attachments.stream()
                .filter(attachment -> attachment.filePath != null && !attachment.filePath.isBlank())
                .map(attachment -> attachment.filePath)
                .toList();
    return Optional.of(new PromptInput(value.prompt, files));
  }

  /** Returns the response that rejects a prompt for {@code reason} in the format of {@code agent}. */
  static String block(final String agent, final String reason) {
    final String quoted = NativeHookText.quote(reason);
    return switch (agent) {
      case "cursor" -> "{\"continue\":false,\"user_message\":" + quoted + "}";
      case "gemini" -> "{\"decision\":\"deny\",\"reason\":" + quoted + "}";
      default -> BLOCK + quoted + "}";
    };
  }

  /** A prompt-submit payload: the event, the prompt, and attached files where there are any. */
  @JsonType
  public static final class PromptPayload {
    @JsonProperty("hook_event_name")
    public String hookEventName;

    public String prompt;

    public List<Attachment> attachments;
  }

  /** A file or rule a prompt attaches. */
  @JsonType
  public static final class Attachment {
    @JsonProperty("file_path")
    public String filePath;
  }
}
