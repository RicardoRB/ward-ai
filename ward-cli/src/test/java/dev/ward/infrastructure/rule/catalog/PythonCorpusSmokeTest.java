package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DuplicateCodeFindings;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs Ward's Python rules (the {@code standard} preset and every security rule) over the source of
 * Healthchecks, a Django application (BSD-3-Clause), and compares the per-rule counts with the
 * committed report {@code benchmark/python-corpus-report.txt}. It is skipped unless {@code
 * -Dward.benchmark.python-corpus=<extracted archive>} is set; {@code
 * -Dward.benchmark.python-corpus.archive} names the archive to check, and {@code
 * -Dward.benchmark.write=true} rewrites the report, lists the findings that still need a label in
 * {@code target/python-corpus-unlabelled.txt} and every finding in {@code
 * target/python-corpus-findings.txt}.
 *
 * <p>The test fails when a file is left without analysis. The label of a finding (true positive,
 * false positive or undetermined) was decided by reading the code at that line and is committed in
 * {@code benchmark/python-corpus-labels.txt}. Every finding of a security rule needs a label; of
 * another rule, the first {@value PythonCorpusReport#SAMPLE} findings in path order. A finding of that set without a
 * label, or a label without such a finding, fails the test. The source is only read: nothing in it
 * is committed, installed or run.
 */
class PythonCorpusSmokeTest {
  private static final String REPORT = "benchmark/python-corpus-report.txt";
  private static final String UNLABELLED = "target/python-corpus-unlabelled.txt";
  private static final String FINDINGS = "target/python-corpus-findings.txt";
  private static final String PREFIX = "ward:python/";

  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.python-corpus", matches = ".+")
  void pythonCorpusReportMatchesTheCommittedOne() throws IOException {
    final String archive = System.getProperty("ward.benchmark.python-corpus.archive");
    if (archive != null) {
      assertThat(JulietCorpus.sha256(Path.of(archive))).isEqualTo(PythonCorpus.ARCHIVE_SHA256);
    }
    final List<SourceUnit> units = PythonCorpus.units(
        Path.of(System.getProperty("ward.benchmark.python-corpus"))
    );
    final List<RuleDefinition> rules = rules();
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    final RuleEngine engine = new RuleEngine(BundledRuleMatchers.create());
    final RuleRunResult result = engine.run(units, frontends, rules);
    final List<AnalysisFinding> findings = new ArrayList<>(result.findings());
    findings.addAll(engine.runProject(units, frontends, rules));
    findings.addAll(DuplicateCodeFindings.findings(rules, units, frontends));
    findings.addAll(
        UnusedDefinitionFindings.findings(rules, PythonCorpus.summaries(units, frontends))
    );
    final Map<String, List<PythonCorpusReport.Place>> found = PythonCorpusReport.places(findings);
    final Map<PythonCorpusReport.Place, String> labels = PythonCorpusReport.labels();
    final String report = PythonCorpusReport.report(rules, units.size(), found, labels);
    final Path file = Path.of("src/test/resources").resolve(REPORT);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report);
      final Map<PythonCorpusReport.Place, String> messages = PythonCorpusReport.messages(findings);
      Files.writeString(
          Path.of(UNLABELLED),
          PythonCorpusReport.listed(PythonCorpusReport.needed(rules, found), labels, messages)
      );
      final List<PythonCorpusReport.Place> all = found.values().stream().flatMap(
          List::stream
      ).toList();
      Files.writeString(Path.of(FINDINGS), PythonCorpusReport.listed(all, Map.of(), messages));
    }
    assertThat(notAnalysed(result)).as("files left without analysis").isEmpty();
    assertThat(result.notRun()).as("rules not run").isEmpty();
    final List<PythonCorpusReport.Place> needed = PythonCorpusReport.needed(rules, found);
    assertThat(labels.keySet()).as("findings without a label").containsAll(needed);
    assertThat(needed).as("labels without a finding").containsAll(labels.keySet());
    assertThat(report).isEqualTo(Files.readString(file));
  }

  private static List<RuleDefinition> rules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> rule.id().startsWith(PREFIX))
        .filter(
            rule -> PythonCorpusReport.isSecurity(rule) || RulePreset.STANDARD.selects(
                rule.presets()
            )
        )
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }

  private static List<String> notAnalysed(final RuleRunResult result) {
    return result.executionErrors().stream()
        .filter(error -> error.path() != null)
        .map(error -> error.path() + ": " + error.reason())
        .distinct()
        .sorted()
        .toList();
  }
}
