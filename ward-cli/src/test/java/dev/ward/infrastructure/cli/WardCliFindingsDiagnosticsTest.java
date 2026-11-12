package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;

class WardCliFindingsDiagnosticsTest extends WardCliIntegrationSupport {
  // Built from two literals, so that no source file holds a token.
  private static final String SECRET = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";

  private void script(final String name, final String body) throws IOException {
    final Path file = project.resolve(name);
    Files.writeString(file, "#!/bin/sh\n" + body);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  @Test
  void findingDiagnosticsNameEventsButNeverContentsOrPaths() throws IOException {
    Files.createDirectories(project.resolve("config"));
    Files.writeString(project.resolve("config/app.yml"), "token: " + SECRET + "\n");
    Files.writeString(
        project.resolve(WARD_TOML),
        """
        version = 2
        [checks.secrets]
        uses = "secrets"
        """);
    final Path report = project.resolve("out/ward.sarif");

    run(
        "--verbose",
        CHECK,
        FORMAT_OPTION,
        "sarif",
        "--output",
        report.toString(),
        "-C",
        project.toString());
    final List<String> messages = err.toString().lines().toList();

    assertThat(messages)
        .anyMatch(message -> message.contains("event=secrets-read files="))
        .anyMatch(message -> message.contains("event=secrets-run files="))
        .anyMatch(message -> message.contains("event=report-written file=ward.sarif"))
        .allSatisfy(
            message ->
                assertThat(message)
                    .doesNotContain(
                        SECRET, SECRET.substring(0, 8), "config/app.yml", project.toString()));
  }
}
