package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Counts the lines of app and library targets in xccov's report, never test bundles. */
class XccovCoverageReportTest {
  private static final Path ROOT = Path.of("/repo/shop");
  private static final Project PROJECT =
      new Project(ROOT, Language.SWIFT, BuildTool.XCODE, Framework.NONE);
  private static final long APP_COVERED = 30;
  private static final long APP_LINES = 50;
  private static final long APP_PERCENT = 60;
  private static final long IOS_APP_LINES = 4;

  private final XccovCoverageReport report = new XccovCoverageReport(new ForyJsonCodec());

  @Test
  void countsTheAppTargetAndLeavesOutTheTestBundle() {
    final LineCoverage coverage =
        report.read(PROJECT, SwiftFixtures.read("swift/xccov-report.json", ROOT));

    assertThat(coverage.usable()).isTrue();
    assertThat(coverage.covered()).isEqualTo(APP_COVERED);
    assertThat(coverage.executable()).isEqualTo(APP_LINES);
    assertThat(coverage.percent()).isEqualTo(APP_PERCENT);
  }

  @Test
  void iosTestBundleLayoutIsAlsoLeftOut() {
    final String stdout =
        """
        {"targets": [
          {"buildProductPath": "/d/Debug-iphonesimulator/Shop.app/Shop",
           "coveredLines": 3, "executableLines": 4},
          {"buildProductPath": "/d/Debug-iphonesimulator/ShopTests.xctest/ShopTests",
           "coveredLines": 9, "executableLines": 9}
        ]}
        """;

    assertThat(report.read(PROJECT, stdout).executable()).isEqualTo(IOS_APP_LINES);
  }

  @Test
  void onlyTestBundlesOrNoTargetsIsUnusable() {
    final String testsOnly =
        "{\"targets\": [{\"buildProductPath\": \"/d/T.xctest/T\", \"coveredLines\": 1,"
            + " \"executableLines\": 1}]}";

    assertThat(report.read(PROJECT, testsOnly).problem()).contains("no executable production");
    assertThat(report.read(PROJECT, "{\"targets\": []}").usable()).isFalse();
  }

  @Test
  void missingOrUnreadableOutputIsUnusable() {
    assertThat(report.read(PROJECT, "").problem()).contains("xcodebuild-test");
    assertThat(report.read(PROJECT, "{not json").problem()).contains("cannot be parsed");
  }
}
