package dev.ward.infrastructure.cli;

import dev.ward.application.license.DocumentOutput;
import dev.ward.application.license.LicenseFixtures;
import dev.ward.application.license.LicenseService;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine;

/** Runs a command with captured output and collects what it writes to files. */
public final class CommandFixtures {
  /** The files the command wrote, by path. */
  public final Map<Path, String> written = new HashMap<>();

  /** Standard output of the last run. */
  public final StringWriter out = new StringWriter();

  /** Standard error of the last run. */
  public final StringWriter err = new StringWriter();

  /** The output port that records files instead of writing them. */
  public final DocumentOutput output = new DocumentOutput(written::put);

  /** A license service over one runtime package with a license and one test package without. */
  public static LicenseService licenses() {
    return LicenseFixtures.service(
        LicenseFixtures.inventory(
            LicenseFixtures.dependency(
                Ecosystem.NPM, "left-pad", "1.3.0", DependencyScope.RUNTIME, "package.json"),
            LicenseFixtures.dependency(
                Ecosystem.NPM, "mystery", "1.0.0", DependencyScope.TEST, "package.json")),
        Map.of("left-pad", LicenseFixtures.declared("MIT")));
  }

  /** Executes {@code command} with {@code args} and returns its exit code. */
  public int run(final Object command, final String... args) {
    final CommandLine line = new CommandLine(command);
    line.setOut(new PrintWriter(out, true));
    line.setErr(new PrintWriter(err, true));
    return line.execute(args);
  }
}
