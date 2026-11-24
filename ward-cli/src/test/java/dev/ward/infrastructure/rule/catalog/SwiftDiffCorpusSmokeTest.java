package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Swift rules of the {@code strict} preset and every security rule over Vapor (MIT), a project of
 * the differential corpus of #760, and compares the per-rule counts with the committed report
 * {@code benchmark/swiftdiff-corpus-report.txt}, as {@link CorpusRun} describes. Only the findings of
 * security rules are labelled; the others are reviewed through {@code corpus/diff-findings.sh}. A
 * finding labelled {@code fp} fails the run unless {@code benchmark/swiftdiff-corpus-approximations.txt}
 * lists it with a reason and an open issue. It is skipped unless {@code -Dward.corpus.swiftdiff=<dir>}
 * names the directory that holds the extracted archive.
 */
class SwiftDiffCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.swiftdiff", matches = ".+")
  void swiftdiffCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun(
        "SwiftDiff",
        PinnedCorpus.swift(),
        RulePreset.STRICT,
        "Vapor MIT"
    );

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
