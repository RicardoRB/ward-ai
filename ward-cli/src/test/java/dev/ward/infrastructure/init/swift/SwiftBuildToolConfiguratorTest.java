package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Swift init writes no build file or tools package, and stops only on an unsupported machine. */
class SwiftBuildToolConfiguratorTest {
  private static final SwiftToolchainInspection MAC_XCODE_26 = SwiftToolchainFakes.mac("26.6");

  @TempDir Path root;

  @Test
  void applyWritesNoBuildFileAndNoToolsPackage() {
    final BuildFileChange change = configurator(MAC_XCODE_26).apply(swiftPm(), List.of());

    assertThat(change.file()).isNull();
    assertThat(root.resolve(".ward")).doesNotExist();
  }

  @Test
  void planLeavesTheManifestUnchanged() {
    final PlannedFileChange plan =
        configurator(MAC_XCODE_26).plan(swiftPm(), List.of(), QualityPreset.STANDARD, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.UNCHANGED);
    assertThat(plan.file()).isEqualTo(root.resolve("Package.swift"));
    assertThat(root.resolve(".ward")).doesNotExist();
  }

  @Test
  void aNonMacOsMachineIsBlockedBeforeAnyChange() throws Exception {
    final SwiftBuildToolConfigurator configurator = configurator(SwiftToolchainFakes.notMac());

    assertThat(configurator.plan(swiftPm(), List.of(), QualityPreset.STANDARD, false).action())
        .isEqualTo(PlannedFileChange.Action.CONFLICT);
    assertThatThrownBy(() -> configurator.apply(swiftPm(), List.of()))
        .hasMessageContaining("macOS only");
    assertThat(Files.list(root)).isEmpty();
  }

  private SwiftBuildToolConfigurator configurator(final SwiftToolchainInspection toolchain) {
    return new SwiftBuildToolConfigurator(toolchain);
  }

  private Project swiftPm() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
