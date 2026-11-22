package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Rust rules of the {@code strict} preset and every Rust security rule over two pinned
 * open-source projects ({@link PinnedCorpus#rust()}) and compares the per-rule counts with the
 * committed report {@code benchmark/rust-corpus-report.txt}, as {@link CorpusRun} describes. A
 * finding labelled {@code fp} fails the run unless {@code benchmark/rust-corpus-approximations.txt}
 * lists it with a reason and an open issue. It is skipped unless {@code
 * -Dward.corpus.rust=<dir>} names the directory that holds both extracted archives.
 */
class RustCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.rust", matches = ".+")
  void rustCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run =
        new CorpusRun(
            "Rust",
            PinnedCorpus.rust(),
            RulePreset.STRICT,
            "ripgrep MIT or Unlicense, tokio MIT"
        );

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
