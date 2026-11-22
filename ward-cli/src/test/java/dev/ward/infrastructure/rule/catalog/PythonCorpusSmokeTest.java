package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs Ward's Python rules (the {@code strict} preset and every security rule) over the source of
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
  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.python-corpus", matches = ".+")
  void pythonCorpusReportMatchesTheCommittedOne() throws IOException {
    PythonCorpusRun.verify(PythonCorpus.HEALTHCHECKS);
  }
}
