package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code ward init} on the offline TypeScript fixture, with stub {@code npm} and {@code npx} first
 * on the path. Ward runs in a child JVM because a JVM cannot change its own {@code PATH}.
 */
@DisabledOnOs(OS.WINDOWS)
class InitTypeScriptProjectTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/typescript-project");
  private static final Path STUBS = Path.of("src/test/resources/fixtures/typescript-project-bin");
  private static final long TIMEOUT_SECONDS = 120;

  @TempDir Path project;

  @BeforeEach
  void copyFixture() throws IOException {
    try (Stream<Path> files = Files.walk(FIXTURE)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        final Path target = project.resolve(FIXTURE.relativize(source));
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
      }
    }
  }

  @Test
  void standardPresetConfiguresTheProjectAndItsPrePushProfilePasses()
      throws IOException, InterruptedException {
    final Result init =
        ward(
            "init",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--ci",
            "github",
            "--agent-rules",
            "--yes",
            "-C",
            project.toString());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(read("ward.toml"))
        .contains(
            "[quality-gates.tsc]",
            "[quality-gates.vitest]",
            "[quality-gates.prettier]",
            "[quality-gates.eslint]",
            "command = \"npx vitest run --coverage --coverage.thresholds.lines=70\"",
            "command = [\"npx\", \"prettier\", \"--check\"]")
        .doesNotContain("checkstyle", "mvnw");
    assertThat(read("package.json"))
        .contains(
            "\"typescript\": \"^5.9.3\",",
            "\"eslint\": \"10.12.0\"",
            "\"typescript-eslint\": \"8.71.1\"",
            "\"prettier\": \"3.9.10\"",
            "\"@vitest/coverage-v8\": \"5.0.3\"");
    assertThat(read("package-lock.json")).contains("\"stub\": \"installed\"");
    assertThat(read("eslint.config.mjs")).startsWith("// managed-by: ward preset=standard\n");
    assertThat(read(".github/workflows/ward.yml"))
        .contains("actions/setup-node@v5", "node-version: \"24\"", "run: npm ci")
        .doesNotContain("setup-java");
    assertThat(read(".gitignore")).startsWith("node_modules/\n").contains(".ward/reports/");
    assertThat(read("AGENTS.md")).contains("ESLint", "Tests (Vitest)").doesNotContain("JaCoCo");
    assertThat(project.resolve("config")).doesNotExist();

    final Result check = ward("check", "--profile", "pre-push", "-C", project.toString());

    assertThat(check.exit()).as(check.output()).isZero();
  }

  private String read(final String file) throws IOException {
    return Files.readString(project.resolve(file));
  }

  private Result ward(final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElse("java"));
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add("dev.ward.WardApplication");
    command.addAll(List.of(args));
    final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder
        .environment()
        .put(
            "PATH",
            STUBS.toAbsolutePath() + File.pathSeparator + System.getenv().getOrDefault("PATH", ""));
    final Process process = builder.start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), output);
  }

  private record Result(int exit, String output) {}
}
