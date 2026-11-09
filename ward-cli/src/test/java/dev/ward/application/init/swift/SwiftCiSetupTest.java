package dev.ward.application.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Swift CI job runs on macOS 26 and sets up no other toolchain or tools package. */
class SwiftCiSetupTest {
  private static final Project SWIFT_PM =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  private static final Project JAVA =
      new Project(Path.of("/api"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void swiftJobRunsOnMacOsWithNoOtherToolchainOrToolsPackage() {
    final String body = CiWorkflowTemplate.render("github", SWIFT_PM, gates(), "0.0.12").body();

    assertThat(body)
        .contains("runs-on: macos-26")
        .doesNotContain(
            "setup-java", "setup-node", "setup-python", ".ward/swift-tools", "swiftlint");
  }

  @Test
  void javaJobStaysOnUbuntu() {
    final String body = CiWorkflowTemplate.render("github", JAVA, gates(), "0.0.12").body();

    assertThat(body).contains("runs-on: ubuntu-24.04").doesNotContain("macos-26");
  }

  private static List<InitGateOption> gates() {
    return List.of(new InitGateOption("swift-build", List.of("swift"), true, "ok"));
  }
}
