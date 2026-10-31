package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Init configures SwiftPM and Xcode projects; Tuist and XcodeGen are named as unsupported. */
class InitToolchainsSwiftTest {
  @Test
  void swiftPmAndXcodeAreHandled() {
    assertThat(InitToolchains.unknown(swift(BuildTool.SWIFTPM))).isFalse();
    assertThat(InitToolchains.unknown(swift(BuildTool.XCODE))).isFalse();
  }

  @Test
  void tuistAndXcodeGenAreNamedAsUnsupported() {
    assertThat(InitToolchains.unknown(swift(BuildTool.TUIST))).isTrue();
    assertThat(InitToolchains.unsupported(swift(BuildTool.TUIST)))
        .contains("Tuist projects are not supported yet")
        .contains("Swift (SwiftPM, Xcode)");
    assertThat(InitToolchains.unknown(swift(BuildTool.XCODEGEN))).isTrue();
    assertThat(InitToolchains.unsupported(swift(BuildTool.XCODEGEN)))
        .contains("XcodeGen projects are not supported yet");
  }

  @Test
  void strictIsRefusedAndSuggestsStandard() {
    final Project project = swift(BuildTool.SWIFTPM);

    assertThatThrownBy(
            () -> InitToolchains.support(project).presetGates(project, QualityPreset.STRICT, null))
        .hasMessageContaining("--preset standard");
  }

  private static Project swift(final BuildTool buildTool) {
    return new Project(Path.of("/shop"), Language.SWIFT, buildTool, Framework.NONE);
  }
}
