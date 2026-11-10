package dev.ward.application.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.PartialCheckDefaults;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Swift test and coverage gates run whole, so the edit hook does not narrow them. */
class SwiftPartialChecksTest {
  private static final Project SWIFT_PM =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);

  @Test
  void testAndCoverageGatesAreNotPartialChecks() {
    assertThat(
            PartialCheckDefaults.forGate(
                "swift-build", SWIFT_PM, QualityPreset.STANDARD, List.of()))
        .isEmpty();
    assertThat(
            PartialCheckDefaults.forGate("swift-test", SWIFT_PM, QualityPreset.STANDARD, List.of()))
        .isEmpty();
    assertThat(
            PartialCheckDefaults.forGate(
                "swift-coverage", SWIFT_PM, QualityPreset.STANDARD, List.of()))
        .isEmpty();
  }
}
