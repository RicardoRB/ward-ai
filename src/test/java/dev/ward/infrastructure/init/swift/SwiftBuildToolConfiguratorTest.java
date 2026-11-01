package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftToolchainInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Resolves Ward's tools package and leaves every project file as it was when resolve fails. */
class SwiftBuildToolConfiguratorTest {
  private static final String PACKAGE = "// swift-tools-version: 6.0\n";
  private static final String PBXPROJ = "// !$*UTF8*$!\n";
  private static final SwiftToolchainInspection MAC_XCODE_26 = SwiftToolchainFakes.mac("26.6");

  @TempDir Path root;

  @Test
  void resolvesTheToolsPackageThroughSwiftPm() throws IOException {
    final FakeCommandExecutor executor = new FakeCommandExecutor(ok());

    final BuildFileChange change = configurator(executor).apply(swiftPm(), List.of());

    assertThat(executor.commands)
        .containsExactly(
            List.of("swift", "package", "--package-path", ".ward/swift-tools", "resolve"));
    assertThat(Files.readString(root.resolve(".ward/swift-tools/Package.swift")))
        .contains("exact: \"0.65.1\"")
        .contains("// managed-by: ward");
    assertThat(change.file()).isEqualTo(root.resolve(".ward/swift-tools/Package.swift"));
  }

  @Test
  void aFailedResolveRemovesTheToolsPackageAndChangesNoProjectFile() throws IOException {
    Files.writeString(root.resolve("Package.swift"), PACKAGE);
    Files.createDirectories(root.resolve("Shop.xcodeproj"));
    Files.writeString(root.resolve("Shop.xcodeproj/project.pbxproj"), PBXPROJ);
    final FakeCommandExecutor executor = new FakeCommandExecutor(failed());
    final Project project = swiftPm();

    assertThatThrownBy(() -> configurator(executor).apply(project, List.of()))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("no files were changed");

    assertThat(root.resolve(".ward")).doesNotExist();
    assertThat(Files.readString(root.resolve("Package.swift"))).isEqualTo(PACKAGE);
    assertThat(Files.readString(root.resolve("Shop.xcodeproj/project.pbxproj"))).isEqualTo(PBXPROJ);
  }

  @Test
  void rollbackRemovesTheToolsPackage() throws IOException {
    final SwiftBuildToolConfigurator configurator = configurator(new FakeCommandExecutor(ok()));
    final BuildFileChange change = configurator.apply(swiftPm(), List.of());

    configurator.rollback(change);

    assertThat(root.resolve(".ward/swift-tools")).doesNotExist();
  }

  @Test
  void planReportsTheResolveWithoutWriting() {
    final PlannedFileChange plan =
        configurator(new FakeCommandExecutor())
            .plan(swiftPm(), List.of(), QualityPreset.STANDARD, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.CREATE);
    assertThat(plan.details()).anyMatch(detail -> detail.contains("0.65.1"));
    assertThat(root.resolve(".ward")).doesNotExist();
  }

  @Test
  void aNonMacOsMachineIsBlockedBeforeAnyChange() {
    final FakeCommandExecutor executor = new FakeCommandExecutor();
    final SwiftBuildToolConfigurator configurator =
        new SwiftBuildToolConfigurator(executor, SwiftToolchainFakes.notMac());

    assertThat(configurator.plan(swiftPm(), List.of(), QualityPreset.STANDARD, false).action())
        .isEqualTo(PlannedFileChange.Action.CONFLICT);
    assertThatThrownBy(() -> configurator.apply(swiftPm(), List.of()))
        .hasMessageContaining("macOS only");
    assertThat(executor.commands).isEmpty();
  }

  private SwiftBuildToolConfigurator configurator(final FakeCommandExecutor executor) {
    return new SwiftBuildToolConfigurator(executor, MAC_XCODE_26);
  }

  private Project swiftPm() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }

  private static CommandResult ok() {
    return new CommandResult(0, "", "", Duration.ZERO);
  }

  private static CommandResult failed() {
    return new CommandResult(1, "", "resolve failed", Duration.ZERO);
  }
}
