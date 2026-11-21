package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Java rules of the {@code strict} preset and every Java security rule over three
 * pinned open-source projects ({@link PinnedCorpus#java()}, one of them a modern Spring Boot
 * application) and compares the per-rule counts with the committed report {@code
 * benchmark/java-corpus-report.txt}, as {@link CorpusRun} describes. A finding labelled {@code fp}
 * fails the run unless {@code benchmark/java-corpus-approximations.txt} lists it with a reason and
 * an open issue. It is skipped unless {@code -Dward.corpus.java=<dir>} names the directory that
 * holds all extracted archives.
 */
class JavaCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.java", matches = ".+")
  void javaCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun("Java", PinnedCorpus.java(), RulePreset.STRICT, "all Apache-2.0");

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
