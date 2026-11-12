package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;

import dev.ward.application.gate.HookCheckFixtures;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.NativeHarnessAdapter;
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
import picocli.CommandLine;

/** Runs {@code hook native} for one adapter in a project with one test check. */
final class NativeHookRun {
  final StringWriter out = new StringWriter();
  final StringWriter err = new StringWriter();
  private final Path root;
  private final NativeHarnessAdapter adapter;

  NativeHookRun(final Path root, final NativeHarnessAdapter adapter) throws IOException {
    this.root = root;
    this.adapter = adapter;
    withPom(root, PLAIN_POM);
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 2\n[checks.tests]\nuses = \"tests\"\nprofiles = [\"default\", \"edit\"]\ncommand = \"mvn test\"\n");
  }

  int run(final int gateExit, final String payload, final String... flags) {
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);
    final CommandExecutor executor =
        (command, directory) -> new CommandResult(gateExit, "", "", Duration.ZERO);
    final NativeHookCommand command =
        new NativeHookCommand(
            new HookChecks(HookCheckFixtures.checks(executor)),
            new ForyJsonCodec(),
            Set.of(adapter),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    final String[] base = {"--agent", adapter.id(), "--event-stdin", "-C", root.toString()};
    final String[] args = new String[base.length + flags.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(flags, 0, args, base.length, flags.length);
    return cli.execute(args);
  }
}
