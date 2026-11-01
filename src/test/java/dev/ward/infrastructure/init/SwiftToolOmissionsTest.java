package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Omits the Swift tool gates whose tool has no row for the selected Xcode, and writes nothing. */
class SwiftToolOmissionsTest {
  @TempDir Path root;

  @Test
  void omitsTheSwiftLintAndHarmonizeGatesOnAnXcodeNoRowCovers() {
    final List<GateOmission> omissions =
        new SwiftBuildToolConfigurator(new FakeCommandExecutor(), SwiftToolchainFakes.mac("28.0"))
            .omissions(swiftPm(), List.of("swift-format", "swiftlint", "harmonize"));

    assertThat(omissions).extracting(GateOmission::gate).containsExactly("swiftlint", "harmonize");
    assertThat(omissions.getFirst().note())
        .isEqualTo(
            "swiftlint omitted: no SwiftLint version is proven on Xcode 28.0; Ward proves it on"
                + " Xcode 26.0 up to 28.0");
  }

  @Test
  void omitsNothingOnAnXcodeAnyRowCovers() {
    final SwiftBuildToolConfigurator configurator =
        new SwiftBuildToolConfigurator(new FakeCommandExecutor(), SwiftToolchainFakes.mac("26.6"));

    assertThat(configurator.omissions(swiftPm(), List.of("swiftlint"))).isEmpty();
  }

  @Test
  void omitsNothingWhenTheXcodeCannotBeRead() {
    final SwiftBuildToolConfigurator configurator =
        new SwiftBuildToolConfigurator(new FakeCommandExecutor(), SwiftToolchainFakes.mac(null));

    assertThat(configurator.omissions(swiftPm(), List.of("swiftlint"))).isEmpty();
  }

  @Test
  void writesNoToolsPackageOnAnXcodeNoRowCovers() {
    final FakeCommandExecutor executor = new FakeCommandExecutor();
    final SwiftBuildToolConfigurator configurator =
        new SwiftBuildToolConfigurator(executor, SwiftToolchainFakes.mac("28.0"));

    final BuildFileChange change = configurator.apply(swiftPm(), List.of());

    assertThat(change.file()).isNull();
    assertThat(executor.commands).isEmpty();
    assertThat(root.resolve(".ward")).doesNotExist();
  }

  private Project swiftPm() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
