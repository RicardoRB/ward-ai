package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Gitleaks 8.30.1 over the fixtures of every secret rule and compares where it
 * reports the rule with the fixture's {@code expected.txt}. The report lists the rules where
 * Gitleaks differs, one line per rule: {@code tool-silent} (nothing on the violating fixture),
 * {@code anchor-differs} (the same number of findings at other positions), {@code count-differs},
 * and {@code tool-extra} (a finding on the passing fixture). A rule where it agrees is not listed.
 * Skipped unless {@code -Dward.parity.gitleaks} names the binary; regenerate the report with {@code
 * -Dward.parity.write=true}.
 */
final class GitleaksParitySmokeTest {

  private static final String GITLEAKS_PROPERTY = "ward.parity.gitleaks";
  private static final String WRITE_PROPERTY = "ward.parity.write";

  private static final int LINE_GROUP = 2;
  private static final int COLUMN_GROUP = 3;
  private static final int FILE_GROUP = 4;
  private static final String REPORT_FILE = "src/test/resources/parity/gitleaks-report.txt";
  private static final String APPROXIMATIONS_FILE =
      "src/test/resources/parity/gitleaks-approximations.txt";
  private static final Pattern ENTRY =
      Pattern.compile(
          "\\{[^{}]*?\"RuleID\":\\s*\"([^\"]+)\"[^{}]*?\"StartLine\":\\s*(\\d+)"
              + "[^{}]*?\"StartColumn\":\\s*(\\d+)[^{}]*?\"File\":\\s*\"([^\"]+)\"",
          Pattern.DOTALL);

  /** Whether the opt-in property names a Gitleaks binary. */
  static boolean gitleaksConfigured() {
    return !System.getProperty(GITLEAKS_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("gitleaksConfigured")
  void theParityReportMatchesTheCommittedOne() throws IOException, InterruptedException {
    final Path root = RuleFixtureSet.root().resolve("secrets");
    final Map<String, List<String>> reported = run(System.getProperty(GITLEAKS_PROPERTY), root);
    final List<String> lines = new ArrayList<>();
    try (Stream<Path> folders = Files.list(root)) {
      for (final Path folder : folders.sorted().toList()) {
        difference(folder, reported)
            .ifPresent(kind -> lines.add(folder.getFileName() + "\t" + kind));
      }
    }
    final String report =
        "# rule, difference: tool-silent (Gitleaks reports nothing on the violating fixture),"
            + " anchor-differs (same number of findings at other positions), count-differs,"
            + " tool-extra (Gitleaks reports the passing fixture).\n"
            + "# Gitleaks 8.30.1 over rules/secrets. Regenerate with -Dward.parity.write=true.\n"
            + String.join("\n", lines)
            + "\n";
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(Path.of(REPORT_FILE), report, StandardCharsets.UTF_8);
    }
    assertThat(report).isEqualTo(Files.readString(Path.of(REPORT_FILE), StandardCharsets.UTF_8));
  }

  /** Returns the positions Gitleaks reports, keyed by rule id and fixture file name. */
  private static Map<String, List<String>> run(final String gitleaks, final Path root)
      throws IOException, InterruptedException {
    final Path report = Files.createTempFile("gitleaks-parity", ".json");
    final Process process =
        new ProcessBuilder(
                gitleaks,
                "dir",
                "--no-banner",
                "--redact",
                "--exit-code",
                "0",
                "--report-format",
                "json",
                "--report-path",
                report.toString(),
                root.toString())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    process.waitFor();
    final String json = Files.readString(report, StandardCharsets.UTF_8);
    Files.delete(report);
    final Map<String, List<String>> positions = new TreeMap<>();
    final Matcher matcher = ENTRY.matcher(json);
    while (matcher.find()) {
      final Path file = Path.of(matcher.group(FILE_GROUP)).toAbsolutePath();
      final String key = matcher.group(1) + "\t" + root.relativize(file);
      record(positions, key, matcher.group(LINE_GROUP) + ":" + matcher.group(COLUMN_GROUP));
    }
    return positions;
  }

  private static void record(
      final Map<String, List<String>> positions, final String key, final String position) {
    positions.computeIfAbsent(key, name -> new ArrayList<>()).add(position);
  }

  private static java.util.Optional<String> difference(
      final Path folder, final Map<String, List<String>> reported) throws IOException {
    final String rule = folder.getFileName().toString();
    final Path violating = fixture(folder, "violating");
    final Path passing = fixture(folder, "passing");
    final List<String> expected =
        Files.readAllLines(folder.resolve("expected.txt")).stream()
            .filter(line -> !line.isBlank())
            .toList();
    final List<String> kinds = new ArrayList<>();
    final List<String> found = positions(reported, rule, folder, violating);
    if (found.isEmpty()) {
      kinds.add("tool-silent");
    } else if (!found.equals(expected)) {
      kinds.add(found.size() == expected.size() ? "anchor-differs" : "count-differs");
    }
    if (!positions(reported, rule, folder, passing).isEmpty()) {
      kinds.add("tool-extra");
    }
    if (kinds.isEmpty()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        approximations().contains(rule) ? "approximation" : String.join(",", kinds));
  }

  private static java.util.Set<String> approximations() throws IOException {
    return Files.readAllLines(Path.of(APPROXIMATIONS_FILE)).stream()
        .filter(line -> !line.startsWith("#") && !line.isBlank())
        .map(line -> line.substring(0, line.indexOf('\t')))
        .collect(java.util.stream.Collectors.toSet());
  }

  private static List<String> positions(
      final Map<String, List<String>> reported,
      final String rule,
      final Path folder,
      final Path file) {
    final Path root = folder.getParent();
    return reported.getOrDefault(rule + "\t" + root.relativize(file), List.of());
  }

  private static Path fixture(final Path folder, final String base) throws IOException {
    try (Stream<Path> files = Files.list(folder)) {
      return files
          .filter(file -> file.getFileName().toString().startsWith(base + "."))
          .sorted()
          .findFirst()
          .orElseThrow();
    }
  }
}
