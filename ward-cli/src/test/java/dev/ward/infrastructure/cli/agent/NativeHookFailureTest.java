package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.HookCheckFixtures;
import dev.ward.domain.FakeAvailableEnvironment;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.CodexIntegration;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Pins the native hook's fail-closed behavior for malformed input and expected failures. */
class NativeHookFailureTest {
  private static final String EVENT_STDIN = "--event-stdin";
  private static final String AGENT = "--agent";
  private static final String CODEX = "codex";
  private static final String DIRECTORY = "-C";

  @TempDir Path root;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();
  private final CommandExecutor passing =
      (command, directory) -> new CommandResult(0, "", "", Duration.ZERO);

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 2\n[checks.tests]\nuses = \"tests\"\nprofiles = [\"default\", \"edit\"]\ncommand = \"mvn test\"\n");
  }

  private int run(final String payload, final String... args) {
    final FakeAvailableEnvironment environment = new FakeAvailableEnvironment(true);
    final NativeHookCommand command =
        new NativeHookCommand(
            new HookChecks(HookCheckFixtures.checks(passing, environment)),
            new ForyJsonCodec(),
            Set.of(new CodexIntegration()),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    return cli.execute(args);
  }

  @Test
  void nullPayloadIsTreatedAsUnreadableAndChecksStillRun() {
    assertThat(run("null", AGENT, CODEX, EVENT_STDIN, DIRECTORY, root.toString())).isZero();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void nonObjectPayloadIsTreatedAsUnreadable() {
    assertThat(run("[1, 2]", AGENT, CODEX, EVENT_STDIN, DIRECTORY, root.toString())).isZero();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void eventWithoutNameIsNotATurnEnd() {
    assertThat(run("{}", AGENT, CODEX, EVENT_STDIN, DIRECTORY, root.toString())).isZero();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void unsupportedAgentFailsClosedWithOneLineMessage() {
    final int exit = run("{}", AGENT, "nope", EVENT_STDIN, DIRECTORY, root.toString());

    assertThat(exit).isEqualTo(2);
    assertThat(err.toString())
        .isEqualTo(
            "Ward native hook could not complete validation: Unsupported native hook agent:"
                + " nope"
                + System.lineSeparator());
  }

  @Test
  void missingConfigurationFailsClosedWithOneLineMessage() throws IOException {
    Files.delete(root.resolve("ward.toml"));

    final int exit = run("{}", AGENT, CODEX, EVENT_STDIN, DIRECTORY, root.toString());

    assertThat(exit).isEqualTo(2);
    assertThat(err.toString())
        .startsWith("Ward native hook could not complete validation: ")
        .doesNotContain("\tat ");
  }
}
