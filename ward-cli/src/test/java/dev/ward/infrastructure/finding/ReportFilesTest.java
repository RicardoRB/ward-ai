package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportFilesTest {
  @TempDir Path root;

  @Test
  void relativizeKeepsOutsidePathsAndHandlesFileUris() {
    assertThat(
            ReportFiles.relativize(FindingFixtures.maven(root), "file://" + root + "/src/A.java"))
        .isEqualTo("src/A.java");
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), "/elsewhere/A.java"))
        .isEqualTo("/elsewhere/A.java");
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), " ")).isNull();
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), null)).isNull();
  }
}
