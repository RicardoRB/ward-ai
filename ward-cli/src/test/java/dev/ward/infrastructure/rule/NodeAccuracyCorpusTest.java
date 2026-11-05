package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Measures the bundled Node security rules on the labeled corpus {@code accuracy/node/}. A file
 * counts for a rule when the rule is named in its label row: the rule is predicted when it reports
 * at least one finding in the file, and the label says whether the file is vulnerable.
 */
class NodeAccuracyCorpusTest {

  private static final Path CORPUS =
      Path.of(System.getProperty("ward.repository.root"), "ward-analysis-bench/accuracy/node");
  private static final int LABEL_FIELDS = 3;

  private static final Counts COMMAND_TS = new Counts(3, 0, 1, 4);
  private static final Counts COMMAND_JS = new Counts(3, 0, 1, 4);
  private static final Counts PATH_TS = new Counts(3, 0, 1, 4);
  private static final Counts PATH_JS = new Counts(3, 0, 1, 4);
  private static final Counts XSS_TS = new Counts(3, 0, 1, 4);
  private static final Counts XSS_JS = new Counts(4, 0, 0, 4);

  /** One row of {@code labels.csv}. */
  private record Label(String file, String rule, boolean vulnerable) {}

  /** The confusion counts of one rule over the files that name it. */
  private record Counts(int truePositive, int falsePositive, int falseNegative, int trueNegative) {

    static final Counts NONE = new Counts(0, 0, 0, 0);

    Counts with(final boolean vulnerable, final boolean predicted) {
      if (vulnerable) {
        return predicted
            ? new Counts(truePositive + 1, falsePositive, falseNegative, trueNegative)
            : new Counts(truePositive, falsePositive, falseNegative + 1, trueNegative);
      }
      return predicted
          ? new Counts(truePositive, falsePositive + 1, falseNegative, trueNegative)
          : new Counts(truePositive, falsePositive, falseNegative, trueNegative + 1);
    }
  }

  /** The counts of the three rules over the corpus, with the exact numbers the corpus gives. */
  @Test
  void countsOfEveryRuleMatchTheLabeledCorpus() throws IOException {
    final Map<String, Counts> counts = new TreeMap<>();
    for (final Label label : labels()) {
      final Counts before = counts.getOrDefault(label.rule(), Counts.NONE);
      counts.put(label.rule(), before.with(label.vulnerable(), predicts(label)));
    }
    assertThat(counts)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "ward:typescript/command-injection", COMMAND_TS,
                "ward:javascript/command-injection", COMMAND_JS,
                "ward:typescript/path-traversal", PATH_TS,
                "ward:javascript/path-traversal", PATH_JS,
                "ward:typescript/xss", XSS_TS,
                "ward:javascript/xss", XSS_JS));
  }

  private static boolean predicts(final Label label) throws IOException {
    final AnalysisLanguage language =
        label.file().endsWith(".ts") ? AnalysisLanguage.TYPESCRIPT : AnalysisLanguage.JAVASCRIPT;
    final String text = Files.readString(CORPUS.resolve(label.file()), StandardCharsets.UTF_8);
    final List<AnalysisFinding> found =
        NodeSecurityRules.run(new SourceUnit("src/" + label.file(), language, text));
    return found.stream().anyMatch(finding -> finding.ruleId().equals(label.rule()));
  }

  private static List<Label> labels() throws IOException {
    final List<Label> labels = new ArrayList<>();
    for (final String line : Files.readAllLines(CORPUS.resolve("labels.csv"))) {
      final String[] fields = line.split(",");
      if (fields.length == LABEL_FIELDS && !"file".equals(fields[0])) {
        labels.add(new Label(fields[0], fields[1], Boolean.parseBoolean(fields[2])));
      }
    }
    return labels;
  }
}
