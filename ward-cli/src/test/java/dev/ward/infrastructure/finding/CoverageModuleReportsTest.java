package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.ConventionalCoverageReports;
import dev.ward.domain.project.BuildTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The conventional report is found in every module directory, so module reports can be summed. */
class CoverageModuleReportsTest {
  @TempDir Path root;

  private List<Path> found(final BuildTool tool) {
    final String conventional = ConventionalCoverageReports.of(tool).orElseThrow();
    return new FileSystemCoverageFiles()
        .moduleDirectories(root).stream()
            .map(directory -> directory.resolve(conventional))
            .filter(Files::isRegularFile)
            .map(root::relativize)
            .toList();
  }

  private void write(final String directory, final String report) throws IOException {
    final Path file = root.resolve(directory).resolve(report);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "x");
  }

  @Test
  void gradleModulesEachWriteTheirOwnReport() throws IOException {
    final String report = "build/reports/jacoco/test/jacocoTestReport.xml";
    write("api", report);
    write("domain", report);

    assertThat(found(BuildTool.GRADLE))
        .containsExactlyInAnyOrder(Path.of("api", report), Path.of("domain", report));
  }

  @Test
  void turborepoMembersEachWriteTheirOwnLcov() throws IOException {
    write("packages/ui", "coverage/lcov.info");
    write("apps/web", "coverage/lcov.info");

    assertThat(found(BuildTool.NPM))
        .containsExactlyInAnyOrder(
            Path.of("packages/ui/coverage/lcov.info"), Path.of("apps/web/coverage/lcov.info"));
  }
}
