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

/** Doctor checks the selected Xcode version on macOS, and warns on other systems. */
class DoctorXcodeVersionTest {
  private static final Path ROOT = Path.of("app");
  private static final SwiftToolchainInspection XCODE_26_6 =
      new FakeSwift(Optional.of("26.6"), true);
  private static final String SWIFT = "swift";
  private static final String XCODE = "xcode";

  @Test
  void supportedXcodeIsOkNamingItsVersion() {
    final List<Finding> findings = diagnose(BuildTool.XCODE, false, XCODE_26_6, SWIFT);

    assertThat(findings).contains(new Finding(XCODE, Status.OK, "Xcode 26.6 selected."));
  }

  @Test
  void oldXcodeIsIncompatible() {
    final List<Finding> findings =
        diagnose(BuildTool.XCODE, false, new FakeSwift(Optional.of("16.2"), true), SWIFT);

    assertThat(findings)
        .contains(
            new Finding(
                XCODE,
                Status.ERROR,
                "Xcode 16.2 is INCOMPATIBLE; Ward needs Xcode 26.0 or newer."));
  }

  @Test
  void unreadableXcodeVersionIsAWarning() {
    final List<Finding> findings =
        diagnose(BuildTool.SWIFTPM, false, new FakeSwift(Optional.empty(), true), SWIFT);

    assertThat(findings)
        .contains(new Finding(XCODE, Status.WARNING, "Could not read the Xcode version."));
  }

  @Test
  void linuxWarnsThatWardVerifiesSwiftOnMacOsOnly() {
    final List<Finding> findings =
        diagnose(BuildTool.SWIFTPM, false, new FakeSwift(Optional.empty(), false), SWIFT);

    assertThat(findings)
        .contains(
            new Finding("platform", Status.WARNING, "Ward verifies Swift projects on macOS only."));
    assertThat(findings).extracting(Finding::name).doesNotContain(XCODE);
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
