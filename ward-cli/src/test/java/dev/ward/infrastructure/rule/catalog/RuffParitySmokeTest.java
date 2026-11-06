package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Ruff on each implemented Python row's fixtures and checks that it reports the lines
 * of the fixture's {@code expected.txt}. Skipped unless {@code -Dward.ruff.binary} names a Ruff
 * executable; the comparison is by line only, since Ruff's columns differ for some rules.
 */
final class RuffParitySmokeTest {

  private static final String RUFF_PROPERTY = "ward.ruff.binary";
  private static final Pattern ROW = Pattern.compile(":(\\d+):\\d+: ");
  private static final int LINE_GROUP = 1;

  /** Whether the opt-in property names a Ruff binary. */
  static boolean ruffConfigured() {
    return !System.getProperty(RUFF_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("ruffConfigured")
  void ruffReportsTheExpectedLinesOfEachPythonFixture() throws IOException, InterruptedException {
    final String ruff = System.getProperty(RUFF_PROPERTY);
    final Path root = RuleFixtureSet.root().resolve("python");
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.PYTHON)) {
        continue;
      }
      final Path directory = root.resolve(row.name());
      if (!Files.exists(directory.resolve("violating.py"))) {
        continue;
      }
      for (final String id : row.counterparts().getOrDefault("ruff", List.of())) {
        problems.addAll(compare(ruff, id, directory));
      }
    }
    assertThat(problems).isEmpty();
  }

  private static List<String> compare(final String ruff, final String id, final Path directory)
      throws IOException, InterruptedException {
    final List<String> problems = new ArrayList<>();
    final List<Integer> expected = expectedLines(directory.resolve("expected.txt"));
    final List<Integer> violating = reportedLines(ruff, id, directory.resolve("violating.py"));
    if (!violating.equals(expected)) {
      problems.add(id + " violating: ruff " + violating + ", fixture " + expected);
    }
    final List<Integer> passing = reportedLines(ruff, id, directory.resolve("passing.py"));
    if (!passing.isEmpty()) {
      problems.add(id + " passing: ruff reports " + passing);
    }
    return problems;
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .toList();
  }

  private static List<Integer> reportedLines(final String ruff, final String id, final Path file)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                ruff,
                "check",
                "--isolated",
                "--no-cache",
                "--select",
                id,
                "--output-format",
                "concise",
                file.toString())
            .redirectErrorStream(false)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final List<Integer> lines = new ArrayList<>();
    final Matcher matcher = ROW.matcher(output);
    while (matcher.find()) {
      lines.add(Integer.parseInt(matcher.group(LINE_GROUP)));
    }
    lines.sort(Comparator.naturalOrder());
    return lines;
  }
}
