package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a built executable's {@code openapi-lint} gate over a seeded spec and over a spec with a
 * syntax error, so that the bundled schemas and the rule files are known to be in the image. Run it
 * with {@code ./mvnw -Dtest=NativeOpenApiLintSmokeTest -Dward.native.binary=target/ward test} after
 * {@code native:compile}.
 */
@EnabledIfSystemProperty(named = "ward.native.binary", matches = ".+")
class NativeOpenApiLintSmokeTest {
  private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
  private static final String WARD_TOML =
      "version = 2\n\n[checks.openapi-lint]\nuses = \"openapi-lint\"\nspec = \"api/openapi.yaml\"\n";

  @TempDir Path project;

  @Test
  void aSeededSpecFailsWithItsFindingsAndASyntaxErrorIsAnExecutionError() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    assertThat(binary).as("ward.native.binary; run native:compile first").isExecutable();
    Files.writeString(
        project.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>shop</groupId>"
            + "<artifactId>shop</artifactId><version>1</version></project>\n");
    Files.writeString(project.resolve("ward.toml"), WARD_TOML);
    write(
        "openapi: 3.0.3\ninfo:\n  title: T\n  version: 1\npaths:\n  /pets:\n    get:\n"
            + "      summary: list\n      responses:\n        \"200\":\n          description: ok\n"
            + "          content:\n            application/json:\n              schema:\n"
            + "                type: string\n                example: 5\n");

    final Run seeded = check(binary);

    assertThat(seeded.exitCode()).as(seeded.stderr()).isNotZero();
    assertThat(seeded.stdout())
        .contains(
            "ward:openapi/operation-operation-id",
            "ward:openapi/oas3-schema",
            "ward:openapi/oas3-valid-schema-example");
    assertThat(seeded.stderr()).doesNotContain("Exception");

    write("openapi: 3.0.3\ninfo: [unclosed\n");
    final Run broken = check(binary);

    assertThat(broken.exitCode()).isNotZero();
    assertThat(broken.stdout() + broken.stderr()).contains("EXECUTION_ERROR");
  }

  private void write(final String spec) throws IOException {
    Files.createDirectories(project.resolve("api"));
    Files.writeString(project.resolve("api/openapi.yaml"), spec);
  }

  private Run check(final Path binary) throws IOException, InterruptedException {
    final Path stdout = Files.createTempFile("ward-out", ".json");
    final Path stderr = Files.createTempFile("ward-err", ".txt");
    final Process process =
        new ProcessBuilder(binary.toString(), "check", "--format", "json", "-C", project.toString())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    try {
      assertThat(process.waitFor(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      return new Run(
          process.exitValue(),
          Files.readString(stdout, StandardCharsets.UTF_8),
          Files.readString(stderr, StandardCharsets.UTF_8));
    } finally {
      process.destroyForcibly();
    }
  }

  private record Run(int exitCode, String stdout, String stderr) {}
}
