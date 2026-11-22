package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Swift rules of the {@code strict} preset, every Swift security rule and the Swift
 * formatter over two pinned open-source projects ({@link SwiftCorpus}) and compares the per-rule
 * counts with the committed report {@code benchmark/swift-corpus-report.txt}. It is skipped unless
 * {@code -Dward.corpus.swift=<dir>} names the directory that holds both extracted archives; {@code
 * -Dward.corpus.swift.archives=<dir>} names the directory of the two archives ({@code <corpus
 * name>.tar.gz}) to check, and {@code -Dward.benchmark.write=true} rewrites the report and writes
 * the findings to {@code target/swift-corpus-findings.txt} for labelling.
 *
 * <p>The test fails when a file is left without analysis, and when the formatter refuses a file or
 * writes one that no longer parses. The labels of {@code benchmark/swift-corpus-labels.txt} were
 * decided by reading the code at each line. A rule with at most {@value #SAMPLE} findings has every
 * finding labelled and a rule with more has at least {@value #SAMPLE}; a label without a finding
 * fails the test.
 */
class SwiftCorpusSmokeTest {
  private static final String REPORT = "benchmark/swift-corpus-report.txt";
  private static final String LABELS = "benchmark/swift-corpus-labels.txt";
  private static final Path RESOURCES = Path.of("src/test/resources");
  private static final Path FINDINGS = Path.of("target/swift-corpus-findings.txt");
  private static final String SECURITY = "security";
  private static final int SAMPLE = 3;
  private static final int FIELDS = 4;
  private static final String SEPARATOR = " \\| ";
  private static final String HEADER =
      "# Ward's Swift rules of the strict preset and every Swift security rule, with default"
          + " options, and the Swift formatter, over every .swift file of two pinned projects"
          + " (both MIT).\n"
          + "%s"
          + "# A labelled finding is true (tp), false (fp) or undetermined (un), as read from the"
          + " code at its line; the labels are in swift-corpus-labels.txt. A rule with more than "
          + SAMPLE
          + " findings has a sample of at least "
          + SAMPLE
          + " labelled, so its tp, fp and un describe the sample.\n"
          + "# The security rules are text patterns (#559) and are listed last, apart.\n"
          + "# The cost of the run is in swift-corpus-cost.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=SwiftCorpusSmokeTest"
          + " -Dward.corpus.swift=<dir> -Djacoco.skip=true -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.swift", matches = ".+")
  void swiftCorpusReportMatchesTheCommittedOne() throws IOException {
    checkArchives();
    final Path directory = Path.of(System.getProperty("ward.corpus.swift"));
    final Map<SwiftCorpus, List<SourceUnit>> corpora = new LinkedHashMap<>();
    for (final SwiftCorpus corpus : SwiftCorpus.all()) {
      corpora.put(corpus, corpus.units(directory));
    }
    final List<SourceUnit> units = corpora.values().stream().flatMap(List::stream).toList();
    final List<RuleDefinition> rules = rules();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules);
    final Map<Place, String> labels = labels();
    final List<Place> found = places(result.findings());
    final List<String> formatProblems = new ArrayList<>();
    final String report =
        report(corpora, rules, result, found, labels) + SwiftCorpusFormat.check(
            units,
            formatProblems
        );
    final Path file = RESOURCES.resolve(REPORT);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report);
      Files.writeString(FINDINGS, findings(result.findings()));
    }
    assertThat(notAnalysed(result)).as("files without analysis").isEmpty();
    assertThat(formatProblems).as("files the formatter refuses or breaks").isEmpty();
    assertThat(found).as("labels without a finding").containsAll(labels.keySet());
    assertThat(underLabelled(rules, found, labels)).as("rules with too few labels").isEmpty();
    assertThat(
        CorpusApproximations.unlisted(RESOURCES.resolve("benchmark"), "swift", found, labels)
    )
        .as("false positives without an approximation with a reason and an open issue")
        .isEmpty();
    assertThat(report).isEqualTo(Files.readString(file));
  }

  private static void checkArchives() throws IOException {
    final String archives = System.getProperty("ward.corpus.swift.archives");
    if (archives == null) {
      return;
    }
    for (final SwiftCorpus corpus : SwiftCorpus.all()) {
      assertThat(JulietCorpus.sha256(Path.of(archives, corpus.name() + ".tar.gz")))
          .as(corpus.name())
          .isEqualTo(corpus.archiveSha256());
    }
  }

  private static List<RuleDefinition> rules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> rule.covers(AnalysisLanguage.SWIFT))
        .filter(
            rule ->
                RulePreset.STRICT.selects(rule.presets()) || SECURITY.equals(rule.category())
        )
        .sorted(
            Comparator.comparing((final RuleDefinition rule) -> SECURITY.equals(rule.category()))
                .thenComparing(RuleDefinition::id)
        )
        .toList();
  }

  /** The files a unit-wide error names, with the reason: a syntax error or a frontend failure. */
  private static List<String> notAnalysed(final RuleRunResult result) {
    return result.executionErrors().stream()
        .filter(error -> error.path() != null)
        .map(
            error ->
                error.path()
                    + ": "
                    + error.reason()
                    + (error.ruleId() == null ? "" : " (" + error.ruleId() + ")")
        )
        .distinct()
        .sorted()
        .toList();
  }

  private static List<Place> places(final List<AnalysisFinding> findings) {
    return findings.stream()
        .map(finding -> new Place(finding.ruleId(), finding.file() + ":" + finding.line()))
        .distinct()
        .sorted(Comparator.comparing(Place::rule).thenComparing(Place::location))
        .toList();
  }

  private static String findings(final List<AnalysisFinding> findings) {
    return findings.stream()
        .map(
            finding ->
                "%s | %s:%s | %s%n"
                    .formatted(finding.ruleId(), finding.file(), finding.line(), finding.message())
        )
        .distinct()
        .sorted()
        .collect(Collectors.joining());
  }

  private static List<String> underLabelled(
      final List<RuleDefinition> rules,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final Map<String, Long> counts =
        found.stream().collect(Collectors.groupingBy(Place::rule, Collectors.counting()));
    final Map<String, Long> labelled =
        labels.keySet().stream()
            .collect(Collectors.groupingBy(Place::rule, Collectors.counting()));
    return rules.stream()
        .map(RuleDefinition::id)
        .filter(
            rule ->
                labelled.getOrDefault(rule, 0L) < Math.min(SAMPLE, counts.getOrDefault(rule, 0L))
        )
        .toList();
  }

  private static String report(
      final Map<SwiftCorpus,
      List<SourceUnit>> corpora,
      final List<RuleDefinition> rules,
      final RuleRunResult result,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final List<String> failed = notAnalysed(result);
    final StringBuilder pins = new StringBuilder();
    final StringBuilder files = new StringBuilder();
    corpora.forEach(
        (corpus, units) -> {
          pins.append(
              "# %s %s, archive sha-256 %s.%n"
                  .formatted(corpus.name(), corpus.pin(), corpus.archiveSha256())
          );
          files.append(
              "%s | files %d | not analysed %d%n"
                  .formatted(
                      corpus.name(),
                      units.size(),
                      failed.stream().filter(line -> line.startsWith(corpus.name() + "/")).count()
                  )
          );
        }
    );
    final StringBuilder report = new StringBuilder(HEADER.formatted(pins)).append(files);
    final Set<String> notRun =
        result.notRun().stream().map(RuleNotRun::ruleId).collect(Collectors.toSet());
    report.append(
        "rules %d | not run (need type facts) %d | findings %d%n"
            .formatted(rules.size(), notRun.size(), found.size())
    );
    for (final RuleDefinition rule : rules) {
      report.append(
          notRun.contains(rule.id())
              ? "%s | not run%n".formatted(rule.id())
              : line(rule, found, labels)
      );
    }
    return report.toString();
  }

  private static String line(
      final RuleDefinition rule,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final String label : List.of("tp", "fp", "un")) {
      counts.put(label, 0);
    }
    int total = 0;
    for (final Place place : found) {
      if (place.rule().equals(rule.id())) {
        total++;
        counts.computeIfPresent(labels.getOrDefault(place, ""), (label, count) -> count + 1);
      }
    }
    return "%s%s | findings %d | labelled %d | tp %d | fp %d | un %d%n"
        .formatted(
            SECURITY.equals(rule.category()) ? "security | " : "",
            rule.id(),
            total,
            counts.values().stream().mapToInt(Integer::intValue).sum(),
            counts.get("tp"),
            counts.get("fp"),
            counts.get("un")
        );
  }

  private static Map<Place, String> labels() throws IOException {
    final Path file = RESOURCES.resolve(LABELS);
    final Map<Place, String> labels = new LinkedHashMap<>();
    if (!Files.exists(file)) {
      return labels;
    }
    Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(text -> !text.startsWith("#") && !text.isBlank())
        .map(text -> text.split(SEPARATOR, FIELDS))
        .forEach(parts -> labels.put(new Place(parts[0].trim(), parts[1].trim()), parts[2].trim()));
    return labels;
  }
}
