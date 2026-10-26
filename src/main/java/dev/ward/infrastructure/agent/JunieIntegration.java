package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.json.JsonCodec;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * Native JetBrains Junie CLI hooks integration contract. Junie reads a project's hooks only when it
 * starts with {@code --config-location .junie/config.json}, and it has no post-tool event, so
 * quality findings arrive at turn end.
 */
public final class JunieIntegration implements NativeHarnessAdapter {
  /** Seconds Junie waits for a quality check; the default is 600 seconds. */
  private static final int CHECK_TIMEOUT_SECONDS = 600;

  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "junie",
          ".junie/config.json",
          ".junie/hooks/ward.sh",
          "PreToolUse,Stop",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD,
              IntegrationCapability.TURN_END,
              IntegrationCapability.FEEDBACK));

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  /**
   * Junie runs commands through {@code sh -c} from the session directory. The documented hook
   * fields are {@code type}, {@code command} and {@code timeout}, so the ownership marker is a
   * shell comment at the end of each command. The wrapper is resolved from the repository root so a
   * session started in a subdirectory still finds it.
   */
  @Override
  public String configContent() {
    return "{\n  \"hooks\": {\n"
        + "    \"PreToolUse\": [{\"matcher\": \"^(Write|Edit|Bash|apply_patch)$\", \"hooks\": ["
        + command("pre", 0)
        + "]}],\n    \"Stop\": [{\"hooks\": ["
        + command("post", CHECK_TIMEOUT_SECONDS)
        + "]}]\n  }\n}\n";
  }

  private static String command(final String phase, final int timeout) {
    return "{\"type\": \"command\", \"command\": \"\\\"$(git rev-parse --show-toplevel 2>/dev/null"
        + " || pwd)/"
        + DEFINITION.hookFile()
        + "\\\" "
        + phase
        + " # "
        + NativeHookText.marker(DEFINITION.id())
        + "\""
        + (timeout == 0 ? "" : ", \"timeout\": " + timeout)
        + "}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  /**
   * Returns {@code reason} unchanged. Junie refuses a tool call only on exit status 2 and shows the
   * agent stderr; a JSON {@code decision: deny} on stdout is logged but does not stop the call.
   */
  public String denyResponse(final String reason) {
    return reason;
  }

  /** A blocking Stop decision makes Junie retry the turn with the reason. */
  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK ->
          "{\"decision\":\"block\",\"reason\":" + NativeHookText.quote(outcome.message()) + "}";
      case ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  @Override
  public boolean deniesWithExitStatus() {
    return true;
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("Stop"));
  }

  /** Junie hook stdin: tool arguments arrive under {@code tool_input}. */
  @JsonType
  public static final class Payload {
    @JsonProperty("hook_event_name")
    public String hookEventName;

    @JsonProperty("tool_name")
    public String toolName;

    @JsonProperty("tool_input")
    public ToolInput toolInput;

    @JsonProperty("stop_hook_active")
    public Boolean stopHookActive;

    NativeHookInput input() {
      final ToolInput arguments = toolInput == null ? new ToolInput() : toolInput;
      return new NativeHookInput(
          hookEventName,
          toolName,
          "",
          arguments.filePath,
          arguments.command,
          arguments.patch,
          null,
          false,
          Boolean.TRUE.equals(stopHookActive));
    }
  }

  /**
   * {@code file_path} for Write and Edit, {@code command} for Bash, and {@code patch} for
   * apply_patch, the tool Junie uses for file edits.
   */
  @JsonType
  public static final class ToolInput {
    @JsonProperty("file_path")
    public String filePath;

    public String command;

    public String patch;
  }
}
