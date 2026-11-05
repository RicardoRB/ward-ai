package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Runs the three Java injection rules in-process over the checked-in accuracy corpus and asserts
 * the exact true positive, false positive and false negative counts. A change that moves them must
 * update these numbers on purpose.
 */
final class JavaAccuracyCorpusTest {

  private static final Path CORPUS =
      Path.of("..", "ward-analysis-bench", "src", "test", "resources", "accuracy", "java");
  private static final String SQL = "ward:java/sql-injection";
  private static final String COMMAND = "ward:java/command-injection";
  private static final String PATH = "ward:java/path-injection";
  private static final int COLUMNS = 3;
  private static final int ZERO = 0;
  private static final int ONE = 1;
  private static final int TWO = 2;
  private static final int SIX = 6;

  /** The counts the rules produce on the corpus, as measured in the accuracy run. */
  @Test
  void countsOnTheCheckedInCorpusAreExact() throws IOException {
    final Set<String> flagged = findings();
    final List<String[]> labels = labels();

    assertThat(counts(SQL, labels, flagged)).containsExactly(SIX, TWO, TWO);
    assertThat(counts(COMMAND, labels, flagged)).containsExactly(SIX, ONE, TWO);
    assertThat(counts(PATH, labels, flagged)).containsExactly(SIX, ZERO, TWO);
  }

  private static Set<String> findings() throws IOException {
    final List<String[]> labels = labels();
    final List<SourceUnit> units = new ArrayList<>();
    for (final String[] label : labels) {
      units.add(unit(label[0]));
    }
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new JavaControlFlow());
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream()
                .filter(
                    rule -> rule.id().startsWith("ward:java/") && rule.id().endsWith("-injection"))
                .toList();
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create()).run(units, List.of(support), rules);
    assertThat(result.executionErrors()).isEmpty();
    final Set<String> found = new HashSet<>();
    for (final AnalysisFinding finding : result.findings()) {
      found.add(finding.file() + "|" + finding.ruleId());
    }
    return found;
  }

  private static SourceUnit unit(final String file) throws IOException {
    final String text = Files.readString(CORPUS.resolve(file), StandardCharsets.UTF_8);
    return new SourceUnit(file, AnalysisLanguage.JAVA, text);
  }

  private static List<String[]> labels() throws IOException {
    final List<String[]> labels = new ArrayList<>();
    final List<String> lines =
        Files.readAllLines(CORPUS.resolve("labels.csv"), StandardCharsets.UTF_8);
    for (final String line : lines.subList(1, lines.size())) {
      final String[] columns = line.split(",", -1);
      assertThat(columns).hasSize(COLUMNS);
      labels.add(columns);
    }
    return labels;
  }

  /** Returns true positives, false positives and false negatives of one rule, per file. */
  private static List<Integer> counts(
      final String rule, final List<String[]> labels, final Set<String> flagged) {
    int truePositives = 0;
    int falseNegatives = 0;
    final Set<String> vulnerable = new HashSet<>();
    for (final String[] label : labels) {
      if (label[1].equals(rule) && Boolean.parseBoolean(label[2])) {
        vulnerable.add(label[0]);
      }
    }
    for (final String file : vulnerable) {
      if (flagged.contains(file + "|" + rule)) {
        truePositives++;
      } else {
        falseNegatives++;
      }
    }
    int falsePositives = 0;
    for (final String entry : flagged) {
      final String[] parts = entry.split("\\|", 2);
      if (parts[1].equals(rule) && !vulnerable.contains(parts[0])) {
        falsePositives++;
      }
    }
    return List.of(truePositives, falsePositives, falseNegatives);
  }
}
