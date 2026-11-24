package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Python rules of the {@code strict} preset and every security rule over the full-stack FastAPI template (MIT), a project of
 * the differential corpus of #760, and compares the per-rule counts with the committed report
 * {@code benchmark/pythondiff-corpus-report.txt}, as {@link CorpusRun} describes. Only the findings of
 * security rules are labelled; the others are reviewed through {@code corpus/diff-findings.sh}. A
 * finding labelled {@code fp} fails the run unless {@code benchmark/pythondiff-corpus-approximations.txt}
 * lists it with a reason and an open issue. It is skipped unless {@code -Dward.corpus.pythondiff=<dir>}
 * names the directory that holds the extracted archive.
 */
class PythonDiffCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.pythondiff", matches = ".+")
  void pythondiffCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun(
        "PythonDiff",
        PinnedCorpus.python(),
        RulePreset.STRICT,
        "FastAPI template MIT"
    );

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
