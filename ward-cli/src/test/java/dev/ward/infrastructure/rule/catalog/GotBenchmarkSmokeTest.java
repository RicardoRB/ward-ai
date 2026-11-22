package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs every TypeScript and JavaScript rule over the source of the HTTP library {@code got} and
 * compares the per-rule counts with the committed report {@code benchmark/got-report.txt}. It is
 * skipped unless {@code -Dward.benchmark.got=<extracted archive>} is set; {@code
 * -Dward.benchmark.got.archive} names the archive to check, and {@code -Dward.benchmark.write=true}
 * rewrites the report.
 *
 * <p>The test fails when a file is left without analysis: every file of the corpus is valid. The
 * label of a finding (true positive, false positive or undetermined) was decided by reading the
 * code at its line and is committed in {@code benchmark/got-labels.txt}. Every finding of a
 * security rule has a label; of the other rules, the first {@value #SAMPLE} findings in path order
 * have one. A sampled finding without a label, or a label without a sampled finding, fails the
 * test.
 */
class GotBenchmarkSmokeTest {
  /** The SHA-256 of the source archive {@code got-v16.2.0.tar.gz} of that tag. */
  static final String ARCHIVE_SHA256 =
      "e562d6c1a290c35861e0052a0aadfbf93ce8c397dfb1155508bd127328f14111";

  /** The findings of one rule that are read and labelled, when the rule is not a security rule. */
  static final int SAMPLE = 3;

  private static final String REPORT = "benchmark/got-report.txt";
  private static final String LABELS = "benchmark/got-labels.txt";
  private static final String TAG = "v16.2.0";

  private static final String SECURITY = "security";
  private static final String TYPESCRIPT_PREFIX = "ward:typescript/";
  private static final String JAVASCRIPT_PREFIX = "ward:javascript/";
  private static final String HEADER =
      "# got %s (MIT) against every TypeScript and JavaScript rule of Ward.\n"
          + "# Archive sha-256 %s.\n"
          + "# Every ward:typescript/* and ward:javascript/* rule ran with its default options,"
          + " whatever its preset, over every .ts, .js and .mjs file outside node_modules.\n"
          + "# A labelled finding is true (tp), false (fp) or undetermined (un), as read from the"
          + " code at its line; the labels are in got-labels.txt. Every finding of a security rule"
          + " is labelled; of another rule, the first %d in path order. Rules without a finding are"
          + " not listed. The cost of the run is in got-cost.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=GotBenchmarkSmokeTest"
          + " -Dward.benchmark.got=<dir> -Dward.benchmark.write=true -Djacoco.skip=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.got", matches = ".+")
  void gotReportMatchesTheCommittedOne() throws IOException {
    final String archive = System.getProperty("ward.benchmark.got.archive");
    if (archive != null) {
      assertThat(JulietCorpus.sha256(Path.of(archive))).isEqualTo(ARCHIVE_SHA256);
    }
    final List<SourceUnit> units =
        new NodeCorpus(Path.of(System.getProperty("ward.benchmark.got"))).units();
    final List<RuleDefinition> rules = rules();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules);
    assertThat(CorpusLabels.notAnalysed(result)).as("files without analysis").isEmpty();
    final List<Place> found = CorpusLabels.places(result.findings());
    final List<Place> sampled = sampled(rules, found);
    final Path resources = Path.of("src/test/resources");
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(Path.of("target/got-sampled.txt"), places(sampled));
      Files.writeString(Path.of("target/got-findings.txt"), places(found));
    }
    final Map<Place, String> labels = CorpusLabels.read(resources.resolve(LABELS));
    assertThat(labels.keySet()).as("labels without a finding").containsAll(sampled);
    assertThat(sampled).as("findings without a label").containsAll(labels.keySet());
    final String report = report(rules, units.size(), found, labels);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(resources.resolve(REPORT), report);
    }
    assertThat(report).isEqualTo(Files.readString(resources.resolve(REPORT)));
    assertThat(
        CorpusApproximations.unlistedIn(
            resources.resolve("benchmark/got-approximations.txt"),
            found,
            labels
        )
    ).as("false positives without an approximation with a reason and an open issue").isEmpty();
  }

  private static List<RuleDefinition> rules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(
            rule ->
                rule.id().startsWith(TYPESCRIPT_PREFIX) || rule.id().startsWith(JAVASCRIPT_PREFIX)
        )
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }

  private static List<Place> sampled(final List<RuleDefinition> rules, final List<Place> found) {
    final List<Place> sampled = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      sampled.addAll(sample(rule, found));
    }
    return sampled;
  }

  private static List<Place> sample(final RuleDefinition rule, final List<Place> found) {
    final List<Place> ofRule =
        found.stream().filter(place -> place.rule().equals(rule.id())).toList();
    return SECURITY.equals(rule.category()) || ofRule.size() <= SAMPLE
        ? ofRule
        : ofRule.subList(0, SAMPLE);
  }

  private static String places(final List<Place> places) {
    final StringBuilder text = new StringBuilder();
    places.forEach(place -> text.append("%s | %s | %n".formatted(place.rule(), place.location())));
    return text.toString();
  }

  private static String report(
      final List<RuleDefinition> rules,
      final int files,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final StringBuilder lines = new StringBuilder();
    int withFindings = 0;
    for (final RuleDefinition rule : rules) {
      final List<Place> sample = sample(rule, found);
      if (!sample.isEmpty()) {
        withFindings++;
        lines.append(
            "%s | findings %d | labelled %d | %s%n"
                .formatted(
                    rule.id(),
                    found.stream().filter(place -> place.rule().equals(rule.id())).count(),
                    sample.size(),
                    CorpusLabels.counts(sample, labels)
                )
        );
      }
    }
    return HEADER.formatted(TAG, ARCHIVE_SHA256, SAMPLE)
        + "files %d | not analysed 0 | rules %d | rules with findings %d | findings %d%n"
            .formatted(files, rules.size(), withFindings, found.size())
        + lines;
  }
}
