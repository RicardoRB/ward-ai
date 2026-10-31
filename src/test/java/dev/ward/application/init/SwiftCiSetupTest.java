package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Swift CI job runs on macOS 26, caches the tools package and sets up no other toolchain. */
class SwiftCiSetupTest {
  private static final Project SWIFT_PM =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  private static final Project JAVA =
      new Project(Path.of("/api"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void swiftJobRunsOnMacOsWithCachedToolsAndNoOtherToolchain() {
    final String body = CiWorkflowTemplate.render("github", SWIFT_PM, gates(), "0.0.12").body();

    assertThat(body)
        .contains("runs-on: macos-26")
        .contains("path: .ward/swift-tools/.build")
        .contains("hashFiles('.ward/swift-tools/Package.resolved')")
        .contains("swift package --package-path .ward/swift-tools resolve")
        .doesNotContain("setup-java", "setup-node", "setup-python");
  }

  @Test
  void javaJobStaysOnUbuntu() {
    final String body = CiWorkflowTemplate.render("github", JAVA, gates(), "0.0.12").body();

    assertThat(body).contains("runs-on: ubuntu-24.04").doesNotContain("macos-26");
  }

  private static List<InitGateOption> gates() {
    return List.of(new InitGateOption("swift-format", List.of("swift"), true, "ok"));
  }
}
