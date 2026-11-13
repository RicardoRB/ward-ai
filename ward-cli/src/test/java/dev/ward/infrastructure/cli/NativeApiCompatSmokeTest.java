package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a built executable's {@code api-compat} gate over a Git repository with one release tag and
 * one public method removed since it. Run it with {@code ./mvnw -Dtest=NativeApiCompatSmokeTest
 * -Dward.native.binary=target/ward test} after {@code native:compile}.
 */
@EnabledIfSystemProperty(named = "ward.native.binary", matches = ".+")
@DisabledOnOs(OS.WINDOWS)
class NativeApiCompatSmokeTest {
  private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
  private static final int FAILED = 1;
  private static final String CLIENT = "src/main/java/shop/Client.java";
  private static final String KOTLIN_CLIENT = "src/main/kotlin/shop/Client.kt";

  @TempDir Path project;

  @Test
  void apiCompatReportsAMethodRemovedSinceTheLatestReleaseTag()
      throws IOException, InterruptedException {
    write(
        "pom.xml",
        "<project><modelVersion>4.0.0</modelVersion><groupId>shop</groupId>"
            + "<artifactId>shop</artifactId><version>1</version></project>\n");
    write(
        CLIENT,
        "package shop;\n\npublic class Client {\n  public void open() {}\n  public void close() {}\n}\n");

    assertThat(
            removeSinceTheTag(
                CLIENT, "package shop;\n\npublic class Client {\n  public void open() {}\n}\n"))
        .contains("api-compat:member-removed")
        .contains("shop.Client#close");
  }

  @Test
  void apiCompatReportsAKotlinFunctionRemovedSinceTheLatestReleaseTag()
      throws IOException, InterruptedException {
    write("build.gradle.kts", "plugins { kotlin(\"jvm\") }\n");
    write(KOTLIN_CLIENT, "package shop\n\nclass Client {\n  fun open() {}\n  fun close() {}\n}\n");

    assertThat(
            removeSinceTheTag(
                KOTLIN_CLIENT, "package shop\n\nclass Client {\n  fun open() {}\n}\n"))
        .contains("api-compat:member-removed")
        .contains("shop.Client#close");
  }

  /** Commits the project, tags it, commits {@code revised} and returns the stdout of the check. */
  private String removeSinceTheTag(final String path, final String revised)
      throws IOException, InterruptedException {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    assertThat(binary).as("ward.native.binary; run native:compile first").isExecutable();
    write("ward.toml", "version = 2\n\n[checks.api-compat]\nuses = \"api-compat\"\n");
    git("init", "-q");
    git("add", "-A");
    git("commit", "-q", "-m", "release");
    git("tag", "v1.0.0");
    write(path, revised);
    git("add", "-A");
    git("commit", "-q", "-m", "remove close");

    final Path stdout = Files.createTempFile("ward-out", ".json");
    final Path stderr = Files.createTempFile("ward-err", ".txt");
    final Process process =
        new ProcessBuilder(binary.toString(), "check", "--format", "json", "-C", project.toString())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    try {
      assertThat(process.waitFor(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      final String err = Files.readString(stderr, StandardCharsets.UTF_8);
      assertThat(err).doesNotContain("Exception").doesNotContain("unexpected error");
      assertThat(process.exitValue()).as(err).isEqualTo(FAILED);
      return Files.readString(stdout, StandardCharsets.UTF_8);
    } finally {
      process.destroyForcibly();
    }
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = project.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private void git(final String... arguments) throws IOException, InterruptedException {
    final List<String> command =
        new ArrayList<>(
            List.of(
                "git",
                "-c",
                "user.name=Test",
                "-c",
                "user.email=test@example.com",
                "-c",
                "commit.gpgsign=false",
                "-c",
                "tag.gpgsign=false"));
    command.addAll(List.of(arguments));
    final Process process = new ProcessBuilder(command).directory(project.toFile()).start();
    assertThat(process.waitFor(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
  }
}
