package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Checkstyle, PMD, SpotBugs, Error Prone and NullAway on the fixtures of every
 * implemented Java rule and compares what they report with the committed parity report. Skipped
 * unless {@code -Dward.parity.tools} names the folder that {@code parity/resolve-tools.sh} filled.
 * Regenerate the report with {@code -Dward.parity.write=true}.
 */
final class JavaParitySmokeTest {

  private static final String WRITE_PROPERTY = "ward.parity.write";

  /** Whether the opt-in property names the tools. */
  static boolean toolsConfigured() {
    return ParityEnvironment.fromProperties().isPresent();
  }

  @Test
  @EnabledIf("toolsConfigured")
  void theParityReportMatchesTheCommittedOne() throws IOException, InterruptedException {
    final ParityEnvironment environment = ParityEnvironment.fromProperties().orElseThrow();
    final List<ParityTool> tools =
        List.of(
            new CheckstyleParity(),
            new PmdParity(),
            new SpotBugsParity(),
            new ErrorProneParity(false),
            new ErrorProneParity(true));
    final String report = JavaParityReport.render(JavaParityReport.rows(environment, tools));
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(
          Path.of("src/test/resources/parity/java-report.txt"), report, StandardCharsets.UTF_8);
    }
    assertThat(JavaParityReport.pinned(report))
        .isEqualTo(JavaParityReport.pinned(JavaParityReport.committed()));
    assertThat(
            JavaParityReport.pinned(report).stream()
                .filter(line -> line.contains("\tposition\t") || line.contains("\ttool-extra\t"))
                .toList())
        .as("disagreements with a tool that no approximation explains")
        .isEmpty();
  }
}
