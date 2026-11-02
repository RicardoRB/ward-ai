package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The blocking Swift findings: a Podfile, an old or unreadable Xcode, and a machine that is not
 * Mac.
 */
class SwiftCompatibilityTest {
  @TempDir Path root;

  @Test
  void aPodfileBlocksAnXcodeProject() throws IOException {
    Files.writeString(root.resolve("Podfile"), "target 'Shop' do\nend\n");
    final Project xcode = new Project(root, Language.SWIFT, BuildTool.XCODE, Framework.NONE);

    assertThat(SwiftCompatibility.problems(xcode, SwiftToolchainFakes.mac("26.6"))).hasSize(1);
    assertThat(SwiftCompatibility.problems(xcode, SwiftToolchainFakes.mac("26.6")).getFirst())
        .contains("CocoaPods");
  }

  @Test
  void anOldXcodeIsIncompatibleNamingTheMinimum() {
    final List<String> problems =
        SwiftCompatibility.problems(swiftPm(), SwiftToolchainFakes.mac("16.2"));

    assertThat(problems).singleElement().asString().contains("INCOMPATIBLE").contains("26.0");
  }

  @Test
  void anUnreadableXcodeVersionIsBlocking() {
    final List<String> problems =
        SwiftCompatibility.problems(swiftPm(), SwiftToolchainFakes.mac(null));

    assertThat(problems).singleElement().asString().contains("Xcode version");
  }

  private Project swiftPm() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
