package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;

import dev.ward.application.gate.CheckService;
import dev.ward.application.gate.HookCheckFixtures;
import dev.ward.application.learning.LearningService;
import dev.ward.domain.FakeAvailableEnvironment;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.learning.JsonLearningStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Builds the hook with real configuration and project files and a recording executor. */
class ClaudeCodeHookTestSupport {
  static final String CONFIG =
      """
      version = 2
      [checks.tests]
      uses = "tests"
      profiles = ["default", "edit"]
      command = "mvn test"
      """;
  @TempDir Path root;

  final StringWriter out = new StringWriter();
  final StringWriter err = new StringWriter();
  final List<List<String>> commands = new ArrayList<>();
  boolean testsPass = true;

  private final CommandExecutor executor =
      (command, directory) -> {
        commands.add(command);
        return new CommandResult(
            testsPass ? 0 : 1, "", testsPass ? "" : "1 test failed", Duration.ZERO);
      };

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(root.resolve("ward.toml"), CONFIG);
  }

  int hook(final String payload) {
    out.getBuffer().setLength(0);
    return run(
        new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
        false,
        "--learn-after",
        "1",
        "--event-stdin",
        "-C",
        root.toString());
  }

  int run(final InputStream input, final boolean interactive, final String... args) {
    final ForyJsonCodec codec = new ForyJsonCodec();
    final EnvironmentInspection environment = new FakeAvailableEnvironment(true);
    final CheckService checks = HookCheckFixtures.checks(executor, environment);
    final ClaudeCodeHookCommand command =
        new ClaudeCodeHookCommand(
            new HookChecks(checks),
            new LearningService(new JsonLearningStore(codec)),
            codec,
            new HookStdin(input, () -> interactive));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    return cli.execute(args);
  }

  String postToolUse(final String path) {
    return """
        {"hook_event_name":"PostToolUse","tool_name":"Edit","tool_input":{"file_path":"%s"}}
        """
        .formatted(path);
  }

  static String stop(final boolean active) {
    return """
        {"hook_event_name":"Stop","stop_hook_active":%s,"last_assistant_message":"done",
         "background_tasks":[],"session_crons":[]}
        """
        .formatted(active);
  }

  static InputStream failingInput() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("stdin must not be read");
      }
    };
  }
}
