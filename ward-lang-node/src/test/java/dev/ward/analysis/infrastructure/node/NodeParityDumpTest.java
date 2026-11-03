package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Writes the structural rules' findings for every fixture as JSON, for the ESLint parity report. It
 * runs only when {@code -Dward.parity.out=<file>} names the output file, as the parity harness in
 * {@code ward-lang-node/parity} does.
 */
class NodeParityDumpTest {

  private static final String OUTPUT_PROPERTY = "ward.parity.out";
  private static final int FIXTURE_COUNT = 36;
  private static final String ROOT = "fixtures/node-structural/";
  private static final List<String> METRICS =
      List.of(
          "function-length",
          "parameter-count",
          "complexity",
          "file-length",
          "naming",
          "nesting-depth");

  @Test
  void dumpsFindingsForEveryFixture() throws IOException {
    final String output = System.getProperty(OUTPUT_PROPERTY);
    Assumptions.assumeTrue(output != null, "set -D" + OUTPUT_PROPERTY + " to dump the parity data");

    final List<String> entries = new ArrayList<>();
    dump(entries, "ts", "ts", AnalysisLanguage.TYPESCRIPT);
    dump(entries, "js-esm", "mjs", AnalysisLanguage.JAVASCRIPT);
    dump(entries, "js-cjs", "cjs", AnalysisLanguage.JAVASCRIPT);
    Files.writeString(Path.of(output), "[\n" + String.join(",\n", entries) + "\n]\n");
    assertThat(entries).hasSize(FIXTURE_COUNT);
  }

  private static void dump(
      final List<String> entries,
      final String directory,
      final String extension,
      final AnalysisLanguage language)
      throws IOException {
    for (final String metric : METRICS) {
      for (final String kind : List.of("violating", "passing")) {
        final String file = directory + "/" + metric + "-" + kind + "." + extension;
        final RuleRunResult result = NodeRuleRuns.run(metric, language, "", read(ROOT + file));
        entries.add(entry(NodeRuleRuns.id(language, metric), file, result));
      }
    }
  }

  private static String entry(final String rule, final String file, final RuleRunResult result) {
    final StringBuilder lines = new StringBuilder();
    for (final AnalysisFinding finding : result.findings()) {
      if (lines.length() > 0) {
        lines.append(',');
      }
      lines.append(finding.line());
    }
    return "  {\"rule\": \"" + rule + "\", \"file\": \"" + file + "\", \"lines\": [" + lines + "]}";
  }

  private static String read(final String path) throws IOException {
    try (InputStream stream = NodeParityDumpTest.class.getResourceAsStream("/" + path)) {
      assertThat(stream).as(path).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
