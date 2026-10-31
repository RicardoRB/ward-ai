package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The edit hooks narrow swift-format and swiftlint to the edited Swift files, not the tests. */
class SwiftPartialChecksTest {
  private static final Project SWIFT_PM =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);

  @Test
  void swiftFormatNarrowsToEditedSwiftFiles() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("swift-format", SWIFT_PM, QualityPreset.STANDARD)
            .orElseThrow();

    assertThat(table.match()).containsExactly("**/*.swift");
    assertThat(table.command()).containsExactly("swift", "format", "lint", "--strict");
    assertThat(table.args()).containsExactly("{paths}");
  }

  @Test
  void swiftLintNarrowsToEditedSwiftFiles() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("swiftlint", SWIFT_PM, QualityPreset.STANDARD).orElseThrow();

    assertThat(table.command())
        .containsExactly(
            ".ward/swift-tools/.build/artifacts/swiftlintplugins/SwiftLintBinary/SwiftLintBinary.artifactbundle/macos/swiftlint",
            "lint",
            "--reporter",
            "json",
            "--quiet");
    assertThat(table.full()).containsExactly(".swiftlint.yml");
  }

  @Test
  void testAndCoverageGatesAreNotPartialChecks() {
    assertThat(PartialCheckDefaults.forGate("swift-build", SWIFT_PM, QualityPreset.STANDARD))
        .isEmpty();
    assertThat(PartialCheckDefaults.forGate("swift-test", SWIFT_PM, QualityPreset.STANDARD))
        .isEmpty();
    assertThat(PartialCheckDefaults.forGate("swift-coverage", SWIFT_PM, QualityPreset.STANDARD))
        .isEmpty();
  }
}
