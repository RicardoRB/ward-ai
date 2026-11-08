package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the parity report of the implemented Java rules against the real tools. */
final class JavaParityReport {

  private static final List<String> TOOLS =
      List.of("checkstyle", "pmd", "spotbugs", "errorprone", "nullaway", "semgrep");

  private static final int NOTE_FIELDS = 4;
  private static final List<String> HEADER =
      List.of(
          "# Java parity report: Ward rules against Checkstyle 14.3.0, PMD 7.29.0, SpotBugs 4.9.7,",
          "# Error Prone 2.50.0 with NullAway 0.14.2, and Semgrep p/java.",
          "# rule, tool, tool id, outcome, note. Regenerate with -Dward.parity.write=true.");

  private JavaParityReport() {}

  /** The implemented Java rules, with the ids their catalog row names in each tool. */
  static List<ParityTarget> targets() throws IOException {
    final List<ParityTarget> targets = new ArrayList<>();
    final Path root = RuleFixtureSet.root();
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.JAVA)) {
        continue;
      }
      targets.add(targetOf(row, root));
    }
    targets.sort((a, b) -> a.name().compareTo(b.name()));
    return targets;
  }

  private static ParityTarget targetOf(final CatalogRow row, final Path root) {
    final RuleFixtureSet fixtures = RuleFixtureSet.of(root, row, AnalysisLanguage.JAVA);
    final Map<String, List<String>> counterparts = new HashMap<>();
    TOOLS.forEach(
        tool -> {
          if (row.counterparts().containsKey(tool)) {
            counterparts.put(tool, row.counterparts().get(tool));
          }
        });
    return new ParityTarget(fixtures.name(), counterparts, fixtures.expected(""));
  }

  /** The documented approximations: {@code rule<TAB>tool<TAB>id<TAB>reason} lines. */
  static Map<String, String> approximations() throws IOException {
    final Map<String, String> notes = new TreeMap<>();
    try (InputStream stream =
        JavaParityReport.class.getResourceAsStream("/parity/java-approximations.txt")) {
      for (final String line :
          new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
        if (!line.isBlank() && !line.startsWith("#")) {
          final String[] parts = line.split("\t", NOTE_FIELDS);
          notes.put(String.join("\t", parts[0], parts[1], parts[2]), parts[NOTE_FIELDS - 1]);
        }
      }
    }
    return notes;
  }

  /** Runs the tools and returns the rows of the report, sorted. */
  static List<ParityRow> rows(final ParityEnvironment environment, final List<ParityTool> tools)
      throws IOException, InterruptedException {
    final List<ParityTarget> targets = targets();
    final Map<String, String> approximations = approximations();
    final List<ParityRow> rows = new ArrayList<>();
    for (final ParityTool tool : tools) {
      final ParityRun run = tool.run(environment, targets);
      for (final ParityTarget target : targets) {
        for (final String id : target.idsOf(tool.name())) {
          rows.add(ParityRow.judge(target, tool, id, run, approximations));
        }
      }
    }
    for (final ParityTarget target : targets) {
      if (TOOLS.stream().allMatch(tool -> target.idsOf(tool).isEmpty())) {
        rows.add(new ParityRow(target.name(), "-", "-", ParityRow.NO_COUNTERPART, ""));
      }
    }
    rows.sort((a, b) -> a.line().compareTo(b.line()));
    return rows;
  }

  /** The report text: a summary of comments, then one line per row. */
  static String render(final List<ParityRow> rows) {
    final Map<String, Integer> counts = new TreeMap<>();
    rows.forEach(row -> counts.merge(row.tool() + " " + row.outcome(), 1, Integer::sum));
    final List<String> lines = new ArrayList<>(HEADER);
    counts.forEach((key, count) -> lines.add("# " + key + ": " + count));
    rows.forEach(row -> lines.add(row.line()));
    return String.join("\n", lines) + "\n";
  }

  /** The lines of a report that the committed copy pins: Semgrep's registry changes over time. */
  static List<String> pinned(final String report) {
    return report
        .lines()
        .filter(line -> !line.startsWith("#") && !line.contains("\tsemgrep\t"))
        .toList();
  }

  /** Reads the committed report. */
  static String committed() throws IOException {
    return Files.readString(
        Path.of("src/test/resources/parity/java-report.txt"), StandardCharsets.UTF_8);
  }
}
