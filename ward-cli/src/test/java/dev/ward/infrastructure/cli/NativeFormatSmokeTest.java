package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a built executable's {@code ward format} over one unformatted file per engine language.
 *
 * <p>The JVM tests cannot see what the native image leaves out: formatter grammars and the parser
 * adapters. This test runs the real binary: {@code --check} finds the files, a write formats them,
 * and a second {@code --check} passes. Run it with {@code ./mvnw -Dtest=NativeFormatSmokeTest
 * -Dward.native.binary=target/ward test} after {@code native:compile}.
 */
@EnabledIfSystemProperty(named = "ward.native.binary", matches = ".+")
class NativeFormatSmokeTest {
  private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
  private static final int OK = 0;
  private static final int FAILED = 1;

  private static final Map<String, String> SOURCES =
      Map.of(
          "src/Main.java", "class Main{int x;}\n",
          "app/Main.kt", "package app\n\nclass Main {\nval x:Int=1\n}\n",
          "web/app.ts", "const a:number=1\n",
          "web/app.js", "const b=2\n",
          "py/main.py", "def f(x):\n      return x\n",
          "ios/App.swift", "struct App{let x:Int}\n");

  @TempDir Path project;

  @Test
  void formatCheckWriteAndRecheckAcrossEveryLanguage() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    assertThat(binary).as("ward.native.binary; run native:compile first").isExecutable();
    writeSources();

    final Run check = format(binary, "--check");
    assertThat(check.exitCode()).as(check.stderr()).isEqualTo(FAILED);
    assertThat(check.stdout()).contains("needs formatting src/Main.java");

    final Run write = format(binary);
    assertThat(write.exitCode()).as(write.stderr()).isEqualTo(OK);
    assertThat(Files.readString(project.resolve("web/app.ts"))).isNotEqualTo("const a:number=1\n");

    final Run recheck = format(binary, "--check");
    assertThat(recheck.exitCode()).as(recheck.stderr()).isEqualTo(OK);
    assertThat(recheck.stderr()).doesNotContain("Exception");
  }

  private void writeSources() throws IOException {
    for (final Map.Entry<String, String> source : SOURCES.entrySet()) {
      final Path file = project.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
    }
  }

  private Run format(final Path binary, final String... options)
      throws IOException, InterruptedException {
    final Path stdout = Files.createTempFile("ward-format-out", ".txt");
    final Path stderr = Files.createTempFile("ward-format-err", ".txt");
    final List<String> command =
        new ArrayList<>(List.of(binary.toString(), "format", "-C", project.toString()));
    command.addAll(List.of(options));
    final Process process =
        new ProcessBuilder(command)
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
