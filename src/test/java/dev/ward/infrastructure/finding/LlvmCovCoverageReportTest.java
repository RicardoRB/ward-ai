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
import org.junit.jupiter.api.io.TempDir;

/** Counts the lines of production files in the export swift test names, never tests or builds. */
class LlvmCovCoverageReportTest {
  private static final String EXPORT = ".build/out/Products/Debug/codecov/Shop.json";
  // Sources/App/Cart.swift in the captured export: 5 of 10 lines covered.
  private static final long CART_COVERED = 5;
  private static final long CART_LINES = 10;

  @TempDir Path root;

  private final LlvmCovCoverageReport report = new LlvmCovCoverageReport(new ForyJsonCodec());

  private Project project() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }

  private LineCoverage read(final String export) {
    FindingFixtures.write(root.resolve(EXPORT), export);
    return report.read(project(), "Building for debugging...\n" + root.resolve(EXPORT) + "\n");
  }

  @Test
  void countsOnlyProductionSourcesOfTheCapturedExport() {
    final LineCoverage coverage = read(SwiftFixtures.read("swift/llvm-cov-export.json", root));

    assertThat(coverage.usable()).isTrue();
    assertThat(coverage.covered()).isEqualTo(CART_COVERED);
    assertThat(coverage.executable()).isEqualTo(CART_LINES);
  }

  @Test
  void projectRelativePathIsResolvedFromTheRoot() {
    FindingFixtures.write(
        root.resolve(EXPORT), SwiftFixtures.read("swift/llvm-cov-export.json", root));

    assertThat(report.read(project(), EXPORT).executable()).isEqualTo(CART_LINES);
  }

  @Test
  void missingExportSaysToRunTheTestsFirst() {
    final LineCoverage coverage = report.read(project(), root.resolve(EXPORT).toString());

    assertThat(coverage.usable()).isFalse();
    assertThat(coverage.problem()).contains(EXPORT, "Run the tests");
  }

  @Test
  void exportWithOnlyTestFilesIsUnusable() {
    final String testsOnly =
        """
        {"type": "llvm.coverage.json.export", "version": "3.0.1", "data": [{"files": [
          {"filename": "%s", "summary": {"lines": {"count": 5, "covered": 5}}},
          {"filename": "%s", "summary": {"lines": {"count": 3, "covered": 1}}}
        ]}]}
        """
            .formatted(
                root.resolve("Tests/AppTests/AppTests.swift"),
                root.resolve("Sources/AppUITests/Helpers.swift"));

    assertThat(read(testsOnly).problem()).contains("no executable production lines");
  }

  @Test
  void noPathOrUnreadableExportIsUnusable() {
    assertThat(report.read(project(), "\n").usable()).isFalse();
    assertThat(read("not json").problem()).contains("cannot be parsed");
  }
}
