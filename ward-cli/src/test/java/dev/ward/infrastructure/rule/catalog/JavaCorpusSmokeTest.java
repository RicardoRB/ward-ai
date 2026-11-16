package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Java rules of the {@code standard} preset and every Java security rule over two
 * pinned open-source projects ({@link PinnedCorpus#java()}) and compares the per-rule counts with the committed report
 * {@code benchmark/java-corpus-report.txt}, as {@link CorpusRun} describes. It is skipped unless
 * {@code -Dward.corpus.java=<dir>} names the directory that holds both extracted archives.
 */
class JavaCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.java", matches = ".+")
  void javaCorpusReportMatchesTheCommittedOne() throws IOException {
    final CorpusRun run = new CorpusRun("Java", PinnedCorpus.java());

    assertThat(run.report()).isEqualTo(run.committed());
  }
}
