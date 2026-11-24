package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the TypeScript and JavaScript rules of the {@code strict} preset and every security rule over NestJS (MIT), a project of
 * the differential corpus of #760, and compares the per-rule counts with the committed report
 * {@code benchmark/nodediff-corpus-report.txt}, as {@link CorpusRun} describes. Only the findings of
 * security rules are labelled; the others are reviewed through {@code corpus/diff-findings.sh}. A
 * finding labelled {@code fp} fails the run unless {@code benchmark/nodediff-corpus-approximations.txt}
 * lists it with a reason and an open issue. It is skipped unless {@code -Dward.corpus.nodediff=<dir>}
 * names the directory that holds the extracted archive.
 */
class NodeDiffCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.nodediff", matches = ".+")
  void nodediffCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun(
        "NodeDiff",
        PinnedCorpus.node(),
        RulePreset.STRICT,
        "NestJS MIT"
    );

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
