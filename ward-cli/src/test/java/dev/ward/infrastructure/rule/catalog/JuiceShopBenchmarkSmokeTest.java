package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

  private static final String REPORT = "benchmark/juice-shop-report.txt";
  private static final String LABELS = "benchmark/juice-shop-labels.txt";
  private static final String SECURITY = "security";
  private static final int FIELDS = 4;
  private static final String SEPARATOR = " \\| ";
  private static final String LANGUAGE_PREFIX = "ward:typescript/";
  private static final String JAVASCRIPT_PREFIX = "ward:javascript/";
  private static final String SYNTAX = "syntax";
  private static final String HEADER =
      "# OWASP Juice Shop %s (MIT) against Ward's TypeScript and JavaScript security rules.\n"
          + "# Archive sha-256 %s.\n"
          + "# Every ward:typescript/* and ward:javascript/* rule of category security ran with"
          + " the strict preset, over every .ts, .tsx, .js and .mjs file outside node_modules.\n"
          + "# A finding is true (tp), false (fp) or undetermined (un), as read from the code at its"
          + " line; the labels are in juice-shop-labels.txt and the categories in"
          + " juice-shop-categories.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=JuiceShopBenchmarkSmokeTest"
          + " -Dward.benchmark.juice-shop=<dir> -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  /** A finding in a place of the source. */
  private record Place(String rule, String location) {}

  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.juice-shop", matches = ".+")
  void juiceShopReportMatchesTheCommittedOne() throws IOException {
    final String archive = System.getProperty("ward.benchmark.juice-shop.archive");
    if (archive != null) {
      assertThat(JulietCorpus.sha256(Path.of(archive))).isEqualTo(JuiceShopCorpus.ARCHIVE_SHA256);
    }
    final List<SourceUnit> units =
        new JuiceShopCorpus(Path.of(System.getProperty("ward.benchmark.juice-shop"))).units();
    final List<RuleDefinition> rules = securityRules();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules);
    final Map<Place, String> labels = labels();
    final List<Place> found = places(result.findings());
    assertThat(labels.keySet()).as("labels without a finding").containsAll(found);
    assertThat(found).as("findings without a label").containsAll(labels.keySet());
    final String report = report(rules, result, units.size(), found, labels);
    final Path file = Path.of("src/test/resources").resolve(REPORT);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report);
    }
    assertThat(report).isEqualTo(Files.readString(file));
  }

  private static List<RuleDefinition> securityRules() {
    return CatalogRuleDefinitions.all().stream()
        .filter(rule -> SECURITY.equals(rule.category()))
        .filter(
            rule ->
                rule.id().startsWith(LANGUAGE_PREFIX) || rule.id().startsWith(JAVASCRIPT_PREFIX))
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }

  private static List<Place> places(final List<AnalysisFinding> findings) {
    return findings.stream()
        .map(finding -> new Place(finding.ruleId(), finding.file() + ":" + finding.line()))
        .distinct()
        .sorted(Comparator.comparing(Place::rule).thenComparing(Place::location))
        .toList();
  }

  private static String report(
      final List<RuleDefinition> rules,
      final RuleRunResult result,
      final int files,
      final List<Place> found,
      final Map<Place, String> labels) {
    final long failed =
        result.executionErrors().stream()
            .filter(error -> error.path() != null && error.reason().contains(SYNTAX))
            .map(RuleExecutionError::path)
            .distinct()
            .count();
    final StringBuilder report =
        new StringBuilder(HEADER.formatted(JuiceShopCorpus.TAG, JuiceShopCorpus.ARCHIVE_SHA256));
    report.append("files %d | not analysed (syntax errors) %d%n".formatted(files, failed));
    for (final RuleDefinition rule : rules) {
      report.append(line(rule.id(), found, labels));
    }
    return report.toString();
  }

  private static String line(
      final String rule, final List<Place> found, final Map<Place, String> labels) {
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final String label : List.of("tp", "fp", "un")) {
      counts.put(label, 0);
    }
    found.stream()
        .filter(place -> place.rule().equals(rule))
        .forEach(place -> counts.merge(labels.get(place), 1, Integer::sum));
    return "%s | findings %d | tp %d | fp %d | un %d%n"
        .formatted(
            rule,
            counts.values().stream().mapToInt(Integer::intValue).sum(),
            counts.get("tp"),
            counts.get("fp"),
            counts.get("un"));
  }

  private static Map<Place, String> labels() throws IOException {
    final Path file = Path.of("src/test/resources").resolve(LABELS);
    final Map<Place, String> labels = new LinkedHashMap<>();
    if (!Files.exists(file)) {
      return labels;
    }
    Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(text -> !text.startsWith("#") && !text.isBlank())
        .map(text -> text.split(SEPARATOR, FIELDS))
        .forEach(parts -> labels.put(place(parts), parts[2].trim()));
    return labels;
  }

  private static Place place(final String... parts) {
    return new Place(parts[0].trim(), parts[1].trim());
  }
}
