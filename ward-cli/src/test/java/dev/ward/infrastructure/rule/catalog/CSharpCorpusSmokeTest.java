package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the C# rules of the {@code strict} preset and every C# security rule over two pinned
 * open-source projects ({@link PinnedCorpus#csharp()}) and compares the per-rule counts with the
 * committed report {@code benchmark/csharp-corpus-report.txt}, as {@link CorpusRun} describes. A
 * finding labelled {@code fp} fails the run unless {@code
 * benchmark/csharp-corpus-approximations.txt} lists it with a reason and an open issue. It is
 * skipped unless {@code -Dward.corpus.csharp=<dir>} names the directory that holds both extracted
 * archives.
 */
class CSharpCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.csharp", matches = ".+")
  void csharpCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run =
        new CorpusRun("CSharp", PinnedCorpus.csharp(), RulePreset.STRICT, "both MIT");

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
