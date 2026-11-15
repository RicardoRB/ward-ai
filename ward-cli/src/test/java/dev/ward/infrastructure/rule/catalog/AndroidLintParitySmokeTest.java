package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Android Lint (the command-line {@code lint} of the Android command-line tools) on
 * the fixtures of each Android rule that follows a Lint check, and compares the lines it reports
 * with the fixture's {@code expected} file. The report has one line per fixture case: {@code agree}
 * (same lines), {@code tool-silent} (Lint reports nothing on the violating case), {@code
 * anchor-differs} or {@code count-differs} (other lines or another number of findings), {@code
 * tool-extra} (Lint reports the passing case), or {@code approximation} for a difference that
 * {@code parity/android-lint-approximations.txt} explains. A difference that is not explained fails
 * the test. Lint needs an Android SDK; the test is skipped unless {@code
 * -Dward.parity.android-lint} names the {@code lint} executable, which must sit in {@code
 * <sdk>/cmdline-tools/<version>/bin}. Regenerate the report with {@code -Dward.parity.write=true}.
 */
final class AndroidLintParitySmokeTest {

  private static final String LINT_PROPERTY = "ward.parity.android-lint";
  private static final String WRITE_PROPERTY = "ward.parity.write";
  private static final String REPORT_FILE = "src/test/resources/parity/android-lint-report.txt";
  private static final String APPROXIMATIONS_FILE =
      "src/test/resources/parity/android-lint-approximations.txt";
  private static final int COLUMNS = 3;
  private static final String AGREE = "agree";

  /** Whether the opt-in property names a Lint executable. */
  static boolean lintConfigured() {
    return !System.getProperty(LINT_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("lintConfigured")
  void theParityReportMatchesTheCommittedOneAndEveryDifferenceIsExplained() throws IOException {
    final Path lint = Path.of(System.getProperty(LINT_PROPERTY)).toAbsolutePath();
    final Path root = RuleFixtureSet.root().resolve("android");
    final Map<String, String> approved = approximations();
    final List<String> lines = new ArrayList<>();
    for (final String rule : AndroidLintRun.rules()) {
      for (final String caseName : cases(root.resolve(rule))) {
        lines.add(rule + "\t" + caseName + "\t" + status(lint, root, rule, caseName, approved));
      }
    }
    final String report =
        "# rule, case, status: agree, tool-silent, anchor-differs, count-differs, tool-extra, or"
            + " approximation (reason in android-lint-approximations.txt).\n"
            + "# Written from a real lint run. Regenerate with -Dward.parity.write=true.\n"
            + String.join("\n", lines)
            + "\n";
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(Path.of(REPORT_FILE), report, StandardCharsets.UTF_8);
    }
    assertThat(report).isEqualTo(Files.readString(Path.of(REPORT_FILE), StandardCharsets.UTF_8));
    assertThat(lines).noneMatch(line -> line.endsWith("\tunexplained"));
  }

  private static Map<String, String> approximations() throws IOException {
    final Map<String, String> approved = new java.util.HashMap<>();
    for (final String line : Files.readAllLines(Path.of(APPROXIMATIONS_FILE))) {
      final String[] parts = line.split("\t", COLUMNS);
      if (!line.startsWith("#") && parts.length == COLUMNS) {
        approved.put(parts[0] + "\t" + parts[1], parts[2]);
      }
    }
    return approved;
  }

  /** The case names of a rule: the empty text, then each {@code violating-<case>} file. */
  private static List<String> cases(final Path directory) throws IOException {
    final List<String> cases = new ArrayList<>(List.of("default"));
    try (Stream<Path> files = Files.list(directory)) {
      files
          .map(file -> file.getFileName().toString())
          .filter(name -> name.startsWith("violating-"))
          .map(name -> name.substring("violating-".length(), name.indexOf('.')))
          .sorted()
          .forEach(cases::add);
    }
    return cases;
  }

  private static String status(
      final Path lint,
      final Path root,
      final String rule,
      final String caseName,
      final Map<String, String> approved)
      throws IOException {
    final Path directory = root.resolve(rule);
    final String suffix = "default".equals(caseName) ? "" : "-" + caseName;
    final List<Integer> expected = expectedLines(directory.resolve("expected" + suffix + ".txt"));
    final List<Integer> violating =
        AndroidLintRun.lines(
            lint, rule, directory, directory.resolve("violating" + suffix + ".xml"));
    final Path passingFile = directory.resolve("passing" + suffix + ".xml");
    final List<Integer> passing =
        Files.exists(passingFile)
            ? AndroidLintRun.lines(lint, rule, directory, passingFile)
            : List.of();
    final String kind = kind(expected, violating, passing);
    if (AGREE.equals(kind)) {
      return kind;
    }
    return approved.containsKey(rule + "\t" + caseName) ? "approximation" : "unexplained";
  }

  private static String kind(
      final List<Integer> expected, final List<Integer> violating, final List<Integer> passing) {
    if (violating.isEmpty()) {
      return "tool-silent";
    }
    if (!violating.equals(expected)) {
      return violating.size() == expected.size() ? "anchor-differs" : "count-differs";
    }
    return passing.isEmpty() ? AGREE : "tool-extra";
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .toList();
  }
}
