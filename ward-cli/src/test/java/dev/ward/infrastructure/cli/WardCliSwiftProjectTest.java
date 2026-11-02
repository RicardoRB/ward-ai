package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** {@code detect} and the JSON report show the target Swift version of a Swift package. */
@DisabledOnOs(OS.WINDOWS)
class WardCliSwiftProjectTest extends WardCliIntegrationSupport {
  @TempDir Path swift;

  @Test
  void detectPrintsTheSwiftVersionOfASwiftPackage() throws IOException {
    swiftPackage();

    assertThat(run("detect", "-C", swift.toString())).isZero();
    assertThat(out.toString())
        .contains("Language:     Swift", "Build tool:   SwiftPM", "Swift:        6.1");
  }

  @Test
  void detectPrintsNoSwiftLineForJava() {
    assertThat(run("detect", "-C", project.toString())).isZero();
    assertThat(out.toString()).doesNotContain("Swift:");
  }

  @Test
  void jsonReportHasTheLanguageVersionOfASwiftPackage() throws IOException {
    swiftPackage();
    Files.writeString(
        swift.resolve(WARD_TOML),
        """
        version = 2
        [checks.format]
        command = ["true"]
        """);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", swift.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get("project").get("languageVersion").asString())
        .isEqualTo("6.1");
  }

  private void swiftPackage() throws IOException {
    Files.writeString(
        swift.resolve("Package.swift"), "// swift-tools-version: 6.1\nimport PackageDescription\n");
    Files.createDirectories(swift.resolve("Sources/App"));
    Files.writeString(swift.resolve("Sources/App/App.swift"), "print(1)\n");
  }
}
