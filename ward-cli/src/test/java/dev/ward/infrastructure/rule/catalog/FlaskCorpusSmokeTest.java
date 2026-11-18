package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs Ward's Python rules over CTFd, a Flask application (Apache-2.0), and compares the per-rule
 * counts with {@code benchmark/flask-corpus-report.txt}, through {@link PythonCorpusRun}. It is
 * skipped unless {@code -Dward.benchmark.flask-corpus=<extracted archive>} is set; {@code
 * -Dward.benchmark.flask-corpus.archive} names the archive to check, and {@code
 * -Dward.benchmark.write=true} rewrites the report. The labels are in {@code
 * benchmark/flask-corpus-labels.txt}, the hand-written cost in {@code
 * benchmark/flask-corpus-cost.txt}. The source is only read: nothing in it is committed, installed
 * or run.
 */
class FlaskCorpusSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.flask-corpus", matches = ".+")
  void flaskCorpusReportMatchesTheCommittedOne() throws IOException {
    PythonCorpusRun.verify(FlaskCorpus.SPEC);
  }
}
