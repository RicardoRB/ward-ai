package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Kotlin rules of the {@code strict} preset and every Kotlin security rule over two
 * pinned open-source projects ({@link PinnedCorpus#kotlin()}) and compares the per-rule counts with the committed report
 * {@code benchmark/kotlin-corpus-report.txt}, as {@link CorpusRun} describes. A finding labelled {@code fp} fails the run unless {@code benchmark/kotlin-corpus-approximations.txt} lists it with a reason and an open issue. It is skipped unless
 * {@code -Dward.corpus.kotlin=<dir>} names the directory that holds both extracted archives.
 */
class KotlinCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.kotlin", matches = ".+")
  void kotlinCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun(
        "Kotlin",
        PinnedCorpus.kotlin(),
        RulePreset.STRICT,
        "both Apache-2.0"
    );

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
