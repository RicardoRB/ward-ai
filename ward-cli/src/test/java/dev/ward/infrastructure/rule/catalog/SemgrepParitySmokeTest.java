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
 * Runs the real Semgrep (the {@code p/javascript} and {@code p/typescript} packs) on each implemented
 * JavaScript row's fixtures and checks that the upstream rule reports the lines of the fixture's
 * {@code expected.txt}. Skipped unless {@code -Dward.semgrep.binary} names a Semgrep executable; the
 * comparison is by line only, since Semgrep reports the start of each match.
 */
final class SemgrepParitySmokeTest {

  private static final String SEMGREP_PROPERTY = "ward.semgrep.binary";
  private static final String TOOL = "semgrep";
  private static final String FIXTURE = "violating.js";
  private static final String PASSING = "passing.js";
  private static final String EXPECTED = "expected.txt";
  private static final Pattern RESULT =
      Pattern.compile(
          "\"check_id\":\"([^\"]+)\"(?:(?!\"check_id\").)*?\"path\":\"[^\"]*\",\"start\":\\{\"line\":(\\d+)",
          Pattern.DOTALL);
  private static final int ID_GROUP = 1;
  private static final int LINE_GROUP = 2;

  /** Whether the opt-in property names a Semgrep binary. */
  static boolean semgrepConfigured() {
    return !System.getProperty(SEMGREP_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("semgrepConfigured")
  void semgrepReportsTheExpectedLinesOfEachJavaScriptFixture()
      throws IOException, InterruptedException {
    final String semgrep = System.getProperty(SEMGREP_PROPERTY);
    final Path root = RuleFixtureSet.root().resolve("javascript");
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.JAVASCRIPT)) {
        problems.addAll(compareRow(semgrep, row, root.resolve(row.name())));
      }
    }
    assertThat(problems).isEmpty();
  }

  private static List<String> compareRow(final String semgrep, final CatalogRow row, final Path dir)
      throws IOException, InterruptedException {
    final List<String> problems = new ArrayList<>();
    for (final String id : row.counterparts().getOrDefault(TOOL, List.of())) {
      final List<Integer> expected = expectedLines(dir.resolve(EXPECTED));
      final List<Integer> violating = reportedLines(semgrep, id, dir.resolve(FIXTURE));
      if (!violating.equals(expected)) {
        problems.add(id + " violating: semgrep " + violating + ", fixture " + expected);
      }
      final List<Integer> passing = reportedLines(semgrep, id, dir.resolve(PASSING));
      if (!passing.isEmpty()) {
        problems.add(id + " passing: semgrep reports " + passing);
      }
    }
    return problems;
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .toList();
  }

  private static List<Integer> reportedLines(final String semgrep, final String id, final Path file)
      throws IOException, InterruptedException {
    final List<String> command =
        List.of(
            semgrep,
            "scan",
            "--config",
            "p/javascript",
            "--config",
            "p/typescript",
            "--json",
            "--metrics=off",
            "--quiet",
            file.toString());
    final Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    return linesOf(output, id);
  }

  /** The lines that the rule {@code id} reports in a Semgrep JSON report, in order. */
  static List<Integer> linesOf(final String json, final String id) {
    final List<Integer> lines = new ArrayList<>();
    final Matcher matcher = RESULT.matcher(json);
    while (matcher.find()) {
      if (matcher.group(ID_GROUP).equals(id)) {
        lines.add(Integer.parseInt(matcher.group(LINE_GROUP)));
      }
    }
    lines.sort(Comparator.naturalOrder());
    return lines;
  }
}
