package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.FixedProjectInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftToolchainInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Doctor checks the Swift tool chain and unsupported Swift setups, not Maven or Gradle. */
class DoctorSwiftTest {
  private static final Path ROOT = Path.of("app");
  private static final SwiftToolchainInspection XCODE_26_6 =
      new FakeSwift(Optional.of("26.6"), true);
  private static final String SWIFT = "swift";
  private static final String XCODEBUILD = "xcodebuild";

  @Test
  void swiftPackageReadyHasOnlySwiftToolLines() {
    final List<Finding> findings = diagnose(BuildTool.SWIFTPM, false, XCODE_26_6, SWIFT);

    assertThat(findings).contains(new Finding(SWIFT, Status.OK, "swift available."));
    assertThat(findings)
        .extracting(Finding::name)
        .doesNotContain("maven", "gradle", XCODEBUILD, "xcrun", "swiftpm");
  }

  @Test
  void swiftMissingIsAnError() {
    final List<Finding> findings = diagnose(BuildTool.SWIFTPM, false, XCODE_26_6);

    assertThat(findings).contains(new Finding(SWIFT, Status.ERROR, "swift is required on PATH."));
  }

  @Test
  void xcodeProjectWithoutXcodebuildIsAnErrorNamingIt() {
    final List<Finding> findings = diagnose(BuildTool.XCODE, false, XCODE_26_6, SWIFT, "xcrun");

    assertThat(findings)
        .contains(new Finding(XCODEBUILD, Status.ERROR, "xcodebuild is required on PATH."))
        .contains(new Finding("xcrun", Status.OK, "xcrun available."));
  }

  @Test
  void tuistWarnsThatWardDoesNotSupportIt() {
    final List<Finding> findings = diagnose(BuildTool.TUIST, false, XCODE_26_6, SWIFT);

    assertThat(findings)
        .contains(new Finding("tuist", Status.WARNING, "Ward does not support Tuist yet."));
  }

  @Test
  void xcodeGenWarnsThatWardDoesNotSupportIt() {
    final List<Finding> findings = diagnose(BuildTool.XCODEGEN, false, XCODE_26_6, SWIFT);

    assertThat(findings)
        .contains(new Finding("xcodegen", Status.WARNING, "Ward does not support XcodeGen yet."));
  }

  @Test
  void podfileWarnsWithoutAnError() {
    final List<Finding> findings =
        diagnose(BuildTool.XCODE, true, XCODE_26_6, SWIFT, XCODEBUILD, "xcrun");

    assertThat(findings)
        .contains(
            new Finding(
                "cocoapods",
                Status.WARNING,
                "CocoaPods trunk is read-only; Ward does not install pods."));
    assertThat(findings)
        .filteredOn(finding -> finding.name().startsWith("x") || SWIFT.equals(finding.name()))
        .extracting(Finding::status)
        .containsOnly(Status.OK);
  }

  @Test
  void xcodeProjectWithoutPodfileHasNoCocoaPodsLine() {
    final List<Finding> findings = diagnose(BuildTool.XCODE, false, XCODE_26_6, SWIFT);

    assertThat(findings).extracting(Finding::name).doesNotContain("cocoapods");
  }

  private static List<Finding> diagnose(
      final BuildTool tool,
      final boolean podfile,
      final SwiftToolchainInspection swift,
      final String... executables) {
    final Project project = new Project(ROOT, Language.SWIFT, tool, Framework.NONE, podfile);
    return new DoctorService(
            new ProjectDetector(new FixedProjectInspection(project)),
            new FakeEnvironmentInspection(executables),
            new DoctorPorts(null, null, null, swift),
            Set.of())
        .diagnose(ROOT);
  }

  private record FakeSwift(Optional<String> xcodeVersion, boolean macOs)
      implements SwiftToolchainInspection {}
}
