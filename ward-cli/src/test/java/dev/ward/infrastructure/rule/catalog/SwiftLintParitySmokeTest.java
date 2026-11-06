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
 * Runs the real SwiftLint on each implemented Swift row's fixtures, with only that rule enabled,
 * and checks that it reports the lines of the fixture's {@code expected.txt} and nothing on the
 * passing fixture. Skipped unless {@code -Dward.swiftlint.binary} names a SwiftLint executable. The
 * comparison is by line only, since SwiftLint marks some findings at a different column.
 */
final class SwiftLintParitySmokeTest {

  private static final String SWIFTLINT_PROPERTY = "ward.swiftlint.binary";
  private static final Pattern LINE = Pattern.compile("\"line\"\\s*:\\s*(\\d+)");
  private static final int LINE_GROUP = 1;

  /** Whether the opt-in property names a SwiftLint binary. */
  static boolean swiftLintConfigured() {
    return !System.getProperty(SWIFTLINT_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("swiftLintConfigured")
  void swiftLintReportsTheExpectedLinesOfEachSwiftFixture()
      throws IOException, InterruptedException {
    final String swiftLint = System.getProperty(SWIFTLINT_PROPERTY);
    final Path root = RuleFixtureSet.root().resolve("swift");
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.SWIFT)) {
        continue;
      }
      final Path directory = root.resolve(row.name());
      if (!Files.exists(directory.resolve("violating.swift"))) {
        continue;
      }
      for (final String id : row.counterparts().getOrDefault("swiftlint", List.of())) {
        problems.addAll(compare(swiftLint, id, directory));
      }
    }
    assertThat(problems).isEmpty();
  }

  private static List<String> compare(final String swiftLint, final String id, final Path directory)
      throws IOException, InterruptedException {
    final List<String> problems = new ArrayList<>();
    final Path config = Files.createTempFile("ward-swiftlint-", ".yml");
    try {
      Files.writeString(config, "only_rules:\n  - " + id + "\n", StandardCharsets.UTF_8);
      final List<Integer> expected = expectedLines(directory.resolve("expected.txt"));
      final List<Integer> violating =
          reportedLines(swiftLint, config, directory.resolve("violating.swift"));
      if (!violating.equals(expected)) {
        problems.add(id + " violating: swiftlint " + violating + ", fixture " + expected);
      }
      final List<Integer> passing =
          reportedLines(swiftLint, config, directory.resolve("passing.swift"));
      if (!passing.isEmpty()) {
        problems.add(id + " passing: swiftlint reports " + passing);
      }
    } finally {
      Files.deleteIfExists(config);
    }
    return problems;
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .toList();
  }

  private static List<Integer> reportedLines(
      final String swiftLint, final Path config, final Path file)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                swiftLint,
                "lint",
                "--quiet",
                "--no-cache",
                "--config",
                config.toString(),
                "--reporter",
                "json",
                file.toString())
            .redirectErrorStream(false)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final List<Integer> lines = new ArrayList<>();
    final Matcher matcher = LINE.matcher(output);
    while (matcher.find()) {
      lines.add(Integer.parseInt(matcher.group(LINE_GROUP)));
    }
    lines.sort(Comparator.naturalOrder());
    return lines;
  }
}
