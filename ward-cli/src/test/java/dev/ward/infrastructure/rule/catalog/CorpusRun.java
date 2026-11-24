package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the rules of the {@code standard} preset and every security rule of one language over its
 * pinned open-source projects ({@link PinnedCorpus}) and compares the per-rule counts with the
 * committed report {@code benchmark/<language>-corpus-report.txt}. {@code
 * -Dward.corpus.<language>=<dir>} names the directory that holds the extracted archives; {@code
 * -Dward.corpus.<language>.archives=<dir>} names the directory of the archives ({@code <corpus
 * name>.tar.gz}) to check, and {@code -Dward.benchmark.write=true} rewrites the report and writes
 * the findings to {@code target/<language>-corpus-findings.txt} for labelling.
 *
 * <p>The run fails when a file is left without analysis. The labels of {@code
 * benchmark/<language>-corpus-labels.txt} were decided by reading the code at each line. A rule
 * with at most {@value #SAMPLE} findings has every finding labelled and a rule with more has at
 * least {@value #SAMPLE}; a label without a finding fails the run. The caller compares the report
 * with the committed one.
 */
final class CorpusRun {
  private static final Path RESOURCES = Path.of("src/test/resources/benchmark");
  private static final String REPORT = "-corpus-report.txt";
  private static final String SECURITY = "security";
  private static final int SAMPLE = 3;
  private static final String HEADER =
      "# Ward's %1$s rules of the %5$s preset and every %1$s security rule, with default"
          + " options, over every %2$s file of %7$d pinned projects (%6$s).\n"
          + "%3$s"
          + "# A labelled finding is true (tp), false (fp) or undetermined (un), as read from the"
          + " code at its line; the labels are in %4$s-corpus-labels.txt. A rule with more than "
          + SAMPLE
          + " findings has a sample of at least "
          + SAMPLE
          + " labelled, so its tp, fp and un describe the sample.\n"
          + "# The cost of the run is in %4$s-corpus-cost.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=%1$sCorpusSmokeTest"
          + " -Dward.corpus.%4$s=<dir> -Djacoco.skip=true -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  private final String title;
  private final String key;
  private final AnalysisLanguage language;
  private final List<PinnedCorpus> pinned;
  private final RulePreset preset;
  private final String licenses;

  /**
   * A run over {@code pinned}, the corpora of one language; {@code title} is the language's name
   * in the report and starts the name of the test class.
   */
  CorpusRun(final String title, final List<PinnedCorpus> pinned) {
    this(title, pinned, RulePreset.STANDARD, "both Apache-2.0");
  }

  /**
   * A run of the rules of {@code preset}. A run of the {@code strict} preset also fails on a
   * finding labelled {@code fp} that {@code benchmark/<language>-corpus-approximations.txt} does
   * not list with its reason and an open issue (#698).
   */
  CorpusRun(
      final String title,
      final List<PinnedCorpus> pinned,
      final RulePreset preset,
      final String licenses
  ) {
    this.preset = preset;
    this.licenses = licenses;
    this.title = title;
    this.key = title.toLowerCase(Locale.ROOT);
    this.language = pinned.get(0).language();
    this.pinned = List.copyOf(pinned);
  }

  /** Returns the committed report. */
  String committed() throws IOException {
    return Files.readString(RESOURCES.resolve(key + REPORT));
  }

  /**
   * Runs the rules over the corpora and returns the report, after checking that every file was
   * analysed and that the labels fit the findings.
   */
  String report() throws IOException {
    checkArchives();
    final Path directory = Path.of(System.getProperty("ward.corpus." + key));
    final Map<PinnedCorpus, List<SourceUnit>> corpora = new LinkedHashMap<>();
    for (final PinnedCorpus corpus : pinned) {
      corpora.put(corpus, corpus.units(directory));
    }
    final List<SourceUnit> units = corpora.values().stream().flatMap(List::stream).toList();
    final List<RuleDefinition> rules = rules();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules);
    final Map<Place, String> labels =
        CorpusLabels.read(RESOURCES.resolve(key + "-corpus-labels.txt"));
    final List<Place> found = CorpusLabels.places(result.findings());
    final String report = text(corpora, rules, result, found, labels);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(RESOURCES.resolve(key + REPORT), report);
      Files.writeString(
          Path.of("target", key + "-corpus-findings.txt"),
          findings(result.findings())
      );
      Files.writeString(
          Path.of("target", key + "-corpus-needed.txt"),
          CorpusSampling.sampled(rules, found).stream()
              .map(place -> place.rule() + " | " + place.location() + "\n")
              .collect(Collectors.joining())
      );
    }
    assertThat(
        notAnalysed(result).stream().filter(
            line -> !CorpusSampling.parserGap(RESOURCES, key, line)
        ).toList()
    )
        .as("files without analysis%n%s", syntaxErrors(units, result))
        .isEmpty();
    assertThat(found).as("labels without a finding").containsAll(labels.keySet());
    assertThat(underLabelled(rules, CorpusSampling.sampled(rules, found), labels))
        .as("rules with too few labels")
        .isEmpty();
    if (preset == RulePreset.STRICT) {
      assertThat(CorpusApproximations.unlisted(RESOURCES, key, found, labels))
          .as("false positives without an approximation with a reason and an open issue")
          .isEmpty();
    }
    return report;
  }

  private void checkArchives() throws IOException {
    final String archives = System.getProperty("ward.corpus." + key + ".archives");
    if (archives == null) {
      return;
    }
    for (final PinnedCorpus corpus : pinned) {
      assertThat(JulietCorpus.sha256(Path.of(archives, corpus.name() + ".tar.gz")))
          .as(corpus.name())
          .isEqualTo(corpus.archiveSha256());
    }
  }

  private List<RuleDefinition> rules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> pinned.get(0).readBy(rule))
        .filter(rule -> preset.selects(rule.presets()) || SECURITY.equals(rule.category()))
        .sorted(Comparator.comparing(RuleDefinition::id))
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

  /** The first syntax diagnostic of every unit that a unit-wide error names. */
  private String syntaxErrors(final List<SourceUnit> units, final RuleRunResult result) {
    final Set<String> failed =
        result.executionErrors().stream()
            .filter(error -> error.ruleId() == null && error.path() != null)
            .map(RuleExecutionError::path)
            .collect(Collectors.toSet());
    final LanguageFrontend frontend =
        new RuleFixtureRunner()
            .frontends().stream()
            .filter(support -> support.language() == language)
            .findFirst()
            .orElseThrow()
            .frontend();
    return units.stream()
        .filter(unit -> failed.contains(unit.path()))
        .flatMap(unit -> frontend.parse(unit).diagnostics().stream().limit(1))
        .map(
            finding ->
                "%s:%d:%d %s".formatted(
                    finding.file(),
                    finding.line(),
                    finding.column(),
                    finding.message()
                )
        )
        .collect(Collectors.joining("\n"));
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

  private String text(
      final Map<PinnedCorpus,
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
    final StringBuilder report = new StringBuilder(
        HEADER.formatted(
            title,
            pinned.get(0).extension(),
            pins,
            key,
            preset.name().toLowerCase(Locale.ROOT),
            licenses,
            pinned.size()
        )
    )
        .append(files);
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
              : line(rule.id(), found, labels)
      );
    }
    return report.toString();
  }

  private static String line(
      final String rule,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final String label : List.of("tp", "fp", "un")) {
      counts.put(label, 0);
    }
    final List<Place> ofRule = new ArrayList<>();
    for (final Place place : found) {
      if (place.rule().equals(rule)) {
        ofRule.add(place);
        counts.computeIfPresent(labels.getOrDefault(place, ""), (label, count) -> count + 1);
      }
    }
    return "%s | findings %d | labelled %d | tp %d | fp %d | un %d%n"
        .formatted(
            rule,
            ofRule.size(),
            counts.values().stream().mapToInt(Integer::intValue).sum(),
            counts.get("tp"),
            counts.get("fp"),
            counts.get("un")
        );
  }
}
