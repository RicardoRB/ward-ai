package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DuplicateCodeFindings;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs Ward's Python rules (the {@code strict} preset and every security rule) over a pinned
 * corpus and compares the per-rule counts with its committed report; the shared body of the Python
 * corpus smoke tests. {@code -Dward.benchmark.write=true} rewrites the report and lists the
 * findings that still need a label in {@code target/<corpus>-unlabelled.txt} and every finding in
 * {@code target/<corpus>-findings.txt}. The source is only read: nothing in it is committed,
 * installed or run.
 */
final class PythonCorpusRun {
  private static final String PREFIX = "ward:python/";

  private PythonCorpusRun() {}

  /** Runs the corpus named by the property of {@code spec} and fails on any difference. */
  static void verify(final PythonCorpusSpec spec) throws IOException {
    final String archive = System.getProperty(spec.property() + ".archive");
    if (archive != null) {
      assertThat(JulietCorpus.sha256(Path.of(archive))).isEqualTo(spec.sha256());
    }
    final Path root = Path.of(System.getProperty(spec.property()));
    final List<SourceUnit> units = PythonCorpus.units(root);
    final List<RuleDefinition> rules = rules();
    final List<FrontendSupport> frontends = new RuleFixtureRunner().frontends();
    final RuleEngine engine = new RuleEngine(BundledRuleMatchers.create());
    final DependencySnapshot snapshot =
        new DependencyAnalysis(frontends, new PythonDependencyAdapters().forProject(root, units))
            .analyze(units, new NoSummaries());
    final RuleRunResult result = engine.run(units, frontends, rules, snapshot);
    final List<AnalysisFinding> findings = new ArrayList<>(result.findings());
    findings.addAll(engine.runProject(units, frontends, rules));
    findings.addAll(DuplicateCodeFindings.findings(rules, units, frontends));
    findings.addAll(
        UnusedDefinitionFindings.findings(
            rules,
            PythonCorpus.summaries(units, frontends),
            path -> java.util.Optional.empty(),
            PythonCorpus.templates(root)
        )
    );
    final Map<String, List<Place>> found = PythonCorpusReport.places(findings);
    final Map<Place, String> labels = PythonCorpusReport.labels(spec);
    final String report = PythonCorpusReport.report(spec, rules, units.size(), found, labels);
    final Path file = Path.of("src/test/resources/benchmark").resolve(spec.report());
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report);
      write(spec, rules, findings, found, labels);
    }
    assertThat(notAnalysed(result)).as("files left without analysis").isEmpty();
    assertThat(result.notRun()).as("rules not run").isEmpty();
    final List<Place> needed = PythonCorpusReport.needed(rules, found);
    assertThat(labels.keySet()).as("findings without a label").containsAll(needed);
    assertThat(needed).as("labels without a finding").containsAll(labels.keySet());
    assertThat(
        CorpusApproximations.unlisted(
            Path.of("src/test/resources/benchmark"),
            spec.report().replace("-corpus-report.txt", ""),
            found.values().stream().flatMap(List::stream).toList(),
            labels
        )
    )
        .as("false positives without an approximation with a reason and an open issue")
        .isEmpty();
    assertThat(report).isEqualTo(Files.readString(file));
  }

  private static void write(
      final PythonCorpusSpec spec,
      final List<RuleDefinition> rules,
      final List<AnalysisFinding> findings,
      final Map<String,
      List<Place>> found,
      final Map<Place,
      String> labels
  ) throws IOException {
    final String name = spec.report().replace("-report.txt", "");
    final Map<Place, String> messages = PythonCorpusReport.messages(findings);
    Files.writeString(
        Path.of("target/" + name + "-unlabelled.txt"),
        PythonCorpusReport.listed(PythonCorpusReport.needed(rules, found), labels, messages)
    );
    final List<Place> all =
        found.values().stream().flatMap(List::stream).toList();
    Files.writeString(
        Path.of("target/" + name + "-findings.txt"),
        PythonCorpusReport.listed(all, Map.of(), messages)
    );
  }

  private static List<RuleDefinition> rules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> rule.id().startsWith(PREFIX))
        .filter(
            rule -> PythonCorpusReport.isSecurity(rule) || RulePreset.STRICT.selects(rule.presets())
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

  /** Keeps no summaries, so the run resolves the project once. */
  private static final class NoSummaries implements FileSummaryCache {
    @Override
    public java.util.Optional<FileSummary> read(final SourceUnit unit) {
      return java.util.Optional.empty();
    }

    @Override
    public void write(final SourceUnit unit, final FileSummary summary) {
      // nothing is kept
    }
  }
}
