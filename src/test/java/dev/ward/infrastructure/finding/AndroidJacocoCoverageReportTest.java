package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Android coverage report sums the line counters of AGP's debug JaCoCo XML reports. */
class AndroidJacocoCoverageReportTest {
  private static final String REPORT = "build/reports/coverage/test/debug/report.xml";
  private static final int APP_COVERED = 30;
  private static final int APP_MISSED = 10;
  private static final int APP_EXECUTABLE = APP_COVERED + APP_MISSED;
  private static final int CORE_COVERED = 20;
  private static final int CORE_MISSED = 0;
  private static final int ALL_COVERED = APP_COVERED + CORE_COVERED;
  private static final int ALL_EXECUTABLE = APP_EXECUTABLE + CORE_COVERED + CORE_MISSED;
  private static final int ABOVE_APP_RATE = 76;
  private static final int AT_APP_RATE = 75;

  @TempDir Path root;

  @Test
  void missingReportIsUnusableNotAPass() {
    final LineCoverage coverage = read();

    assertThat(coverage.usable()).isFalse();
    assertThat(coverage.problem()).contains("No JaCoCo XML report");
  }

  @Test
  void readsTheLineCounterOfTheReportTotals() throws IOException {
    write("app", report(APP_COVERED, APP_MISSED));

    final LineCoverage coverage = read();

    assertThat(coverage.usable()).isTrue();
    assertThat(coverage.covered()).isEqualTo(APP_COVERED);
    assertThat(coverage.executable()).isEqualTo(APP_EXECUTABLE);
    assertThat(coverage.below(ABOVE_APP_RATE)).isTrue();
    assertThat(coverage.below(AT_APP_RATE)).isFalse();
  }

  @Test
  void sumsEveryModuleWithAReport() throws IOException {
    write("app", report(APP_COVERED, APP_MISSED));
    write("core", report(CORE_COVERED, CORE_MISSED));

    final LineCoverage coverage = read();

    assertThat(coverage.covered()).isEqualTo(ALL_COVERED);
    assertThat(coverage.executable()).isEqualTo(ALL_EXECUTABLE);
  }

  @Test
  void reportWithoutExecutableLinesIsUnusable() throws IOException {
    write("app", report(0, 0));

    assertThat(read().problem()).contains("no executable lines");
  }

  @Test
  void unparsableReportIsUnusable() throws IOException {
    write("app", "<report name=\"x\"><counter type=\"LINE\" covered=\"x\" missed=\"0\"/>");

    assertThat(read().usable()).isFalse();
  }

  private LineCoverage read() {
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.ANDROID);
    return new AndroidJacocoCoverageReport().read(project, "");
  }

  private void write(final String module, final String text) throws IOException {
    final Path file = root.resolve(module).resolve(REPORT);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private static String report(final int covered, final int missed) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<report name=\"debug\">\n"
        + "  <package name=\"dev/sample\"><counter type=\"LINE\" missed=\""
        + missed
        + "\" covered=\""
        + covered
        + "\"/></package>\n"
        + "  <counter type=\"LINE\" missed=\""
        + missed
        + "\" covered=\""
        + covered
        + "\"/>\n</report>\n";
  }
}
