package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the TypeScript and JavaScript security rules over the source of OWASP Juice Shop and
 * compares the per-rule counts with the committed report {@code benchmark/juice-shop-report.txt}.
 * It is skipped unless {@code -Dward.benchmark.juice-shop=<extracted archive>} is set; {@code
 * -Dward.benchmark.juice-shop.archive} names the archive to check, and {@code
 * -Dward.benchmark.write=true} rewrites the report.
 *
 * <p>Juice Shop has no list of its vulnerable lines, so the label of each finding (true positive,
 * false positive or undetermined) was decided by reading the code at that line and is committed in
 * {@code benchmark/juice-shop-labels.txt}. A finding without a label, or a label without a finding,
 * fails the test: a new finding needs a reading before it is counted.
 */
class JuiceShopBenchmarkSmokeTest {
  /** The SHA-256 of the source archive {@code juice-shop-v20.4.0.tar.gz} of that tag. */
  static final String ARCHIVE_SHA256 =
      "90551e4bcf83e7019de5bfcba8ab28b35df35b9243d91fbdc7c7e6db30c23e4e";

  private static final String REPORT = "benchmark/juice-shop-report.txt";
  private static final String LABELS = "benchmark/juice-shop-labels.txt";
  private static final String SECURITY = "security";
  private static final String TAG = "v20.4.0";

  private static final String LANGUAGE_PREFIX = "ward:typescript/";
  private static final String JAVASCRIPT_PREFIX = "ward:javascript/";
  private static final String HEADER =
      "# OWASP Juice Shop %s (MIT) against Ward's TypeScript and JavaScript security rules.\n"
          + "# Archive sha-256 %s.\n"
          + "# Every ward:typescript/* and ward:javascript/* rule of category security ran with"
          + " the strict preset and min-confidence = possible, over every .ts, .tsx, .js and .mjs file outside node_modules.\n"
          + "# A finding is true (tp), false (fp) or undetermined (un), as read from the code at its"
          + " line; the labels are in juice-shop-labels.txt and the categories in"
          + " juice-shop-categories.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=JuiceShopBenchmarkSmokeTest"
          + " -Dward.benchmark.juice-shop=<dir> -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.juice-shop", matches = ".+")
  void juiceShopReportMatchesTheCommittedOne() throws IOException {
    final String archive = System.getProperty("ward.benchmark.juice-shop.archive");
    if (archive != null) {
      assertThat(JulietCorpus.sha256(Path.of(archive))).isEqualTo(ARCHIVE_SHA256);
    }
    final NodeCorpus corpus = new NodeCorpus(
        Path.of(System.getProperty("ward.benchmark.juice-shop"))
    );
    final List<SourceUnit> units = corpus.units();
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    final List<RuleDefinition> rules = securityRules();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, frontends, rules, corpus.snapshot(units, frontends));
    final Map<Place, String> labels =
        CorpusLabels.read(Path.of("src/test/resources").resolve(LABELS));
    final List<Place> found = CorpusLabels.places(result.findings());
    assertThat(labels.keySet()).as("labels without a finding").containsAll(found);
    assertThat(found).as("findings without a label").containsAll(labels.keySet());
    final String report = report(rules, result, units.size(), found, labels);
    final Path file = Path.of("src/test/resources").resolve(REPORT);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report);
    }
    assertThat(report).isEqualTo(Files.readString(file));
    assertThat(
        CorpusApproximations.unlistedIn(
            Path.of("src/test/resources/benchmark/juice-shop-approximations.txt"),
            found,
            labels
        )
    ).as("false positives without an approximation with a reason and an open issue").isEmpty();
  }

  private static List<RuleDefinition> securityRules() {
    return CatalogRuleDefinitions.all().stream()
        .filter(rule -> SECURITY.equals(rule.category()))
        .filter(
            rule ->
                rule.id().startsWith(LANGUAGE_PREFIX) || rule.id().startsWith(JAVASCRIPT_PREFIX)
        )
        .map(JuiceShopBenchmarkSmokeTest::withPossibleFlows)
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }

  /** The stored-data flows of userProfile.ts are possible ones, so a taint rule reports them too. */
  private static RuleDefinition withPossibleFlows(final RuleDefinition rule) {
    return rule.options().stream().anyMatch(option -> "min-confidence".equals(option.name()))
        ? RuleOptions.apply(rule, Map.of("min-confidence", "possible"))
        : rule;
  }

  private static String report(
      final List<RuleDefinition> rules,
      final RuleRunResult result,
      final int files,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final StringBuilder report =
        new StringBuilder(HEADER.formatted(TAG, ARCHIVE_SHA256));
    report.append(
        "files %d | not analysed (syntax errors) %d%n"
            .formatted(files, CorpusLabels.notAnalysed(result).size())
    );
    for (final RuleDefinition rule : rules) {
      report.append(line(rule.id(), found, labels));
    }
    return report.toString();
  }

  private static String line(
      final String rule,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) {
    final List<Place> ofRule = found.stream().filter(place -> place.rule().equals(rule)).toList();
    return "%s | findings %d | %s%n"
        .formatted(rule, ofRule.size(), CorpusLabels.counts(ofRule, labels));
  }
}
