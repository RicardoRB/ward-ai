package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.HookScope;
import dev.ward.application.gate.HookCheckFixtures;
import dev.ward.application.learning.LearningService;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.NativeHarnessAdapter;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.learning.JsonLearningStore;
import dev.ward.infrastructure.agent.CodexIntegration;
import dev.ward.infrastructure.agent.CursorIntegration;
import dev.ward.infrastructure.agent.FactoryDroidIntegration;
import dev.ward.infrastructure.agent.GeminiIntegration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import picocli.CommandLine;

/** Runs the real hook commands with the real secret rules and a recording output. */
final class PromptHookRun {
  final StringWriter out = new StringWriter();
  final StringWriter err = new StringWriter();
  private final Path root;

  PromptHookRun(final Path root) {
    this.root = root;
  }

  private HookChecks checks() {
    return new HookChecks(
        HookCheckFixtures.checks(
            (command, directory) -> new CommandResult(0, "", "", Duration.ZERO)
        ),
        HookScope.ALWAYS,
        HookCheckFixtures.prompts()
    );
  }

  int nativeHook(final NativeHarnessAdapter adapter, final String payload) {
    final NativeHookCommand command =
        new NativeHookCommand(
            checks(),
            new ForyJsonCodec(),
            Set.of(adapter),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))
        );
    return execute(
        new CommandLine(command),
        "--agent",
        adapter.id(),
        "--event-stdin",
        "-C",
        root.toString()
    );
  }

  int claude(final String payload) {
    final ForyJsonCodec codec = new ForyJsonCodec();
    final HookStdin stdin =
        new HookStdin(
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
            () -> false
        );
    final ClaudeCodeHookCommand command =
        new ClaudeCodeHookCommand(
            checks(),
            new LearningService(new JsonLearningStore(codec)),
            codec,
            stdin
        );
    return execute(new CommandLine(command), "--event-stdin", "-C", root.toString());
  }

  private int execute(final CommandLine cli, final String... args) {
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    return cli.execute(args);
  }

  static NativeHarnessAdapter adapter(final String id) {
    return switch (id) {
      case "codex" -> new CodexIntegration();
      case "factory-droid" -> new FactoryDroidIntegration();
      case "gemini" -> new GeminiIntegration();
      default -> new CursorIntegration();
    };
  }

  static String secret() throws IOException {
    try (InputStream stream =
        PromptHookRun.class.getResourceAsStream("/rules/secrets/github-pat/violating.txt")) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
  }

  static String payload(final String event, final String prompt) {
    return "{\"hook_event_name\":\"" + event + "\",\"prompt\":" + quote(prompt) + "}";
  }

  static String quote(final String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
