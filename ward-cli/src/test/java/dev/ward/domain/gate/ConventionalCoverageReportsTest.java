package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Toolchain;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ConventionalCoverageReportsTest {

  @Test
  void eachBuildToolHasTheLocationItsCoverageToolWrites() {
    assertThat(ConventionalCoverageReports.of(BuildTool.MAVEN))
        .contains("target/site/jacoco/jacoco.xml");
    assertThat(ConventionalCoverageReports.of(BuildTool.GRADLE))
        .contains("build/reports/jacoco/test/jacocoTestReport.xml");
    assertThat(ConventionalCoverageReports.of(BuildTool.PNPM)).contains("coverage/lcov.info");
    assertThat(ConventionalCoverageReports.of(BuildTool.UV)).contains("coverage.lcov");
  }

  @Test
  void everyJvmNodeAndPythonToolHasOneAndSwiftHasNone() {
    assertThat(Arrays.stream(BuildTool.values()))
        .filteredOn(
            tool ->
                tool.toolchain() == Toolchain.JVM
                    || tool.toolchain() == Toolchain.NODE
                    || tool.toolchain() == Toolchain.PYTHON)
        .allSatisfy(tool -> assertThat(ConventionalCoverageReports.of(tool)).isPresent());
    assertThat(ConventionalCoverageReports.of(BuildTool.SWIFTPM)).isEmpty();
    assertThat(ConventionalCoverageReports.of(BuildTool.XCODE)).isEmpty();
  }
}
