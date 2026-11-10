package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisFindingOrderTest {

  @Test
  void sortsByFileThenLineThenColumnThenRuleThenMessage() {
    final AnalysisFinding late = finding("ward:java/b", "b.java", 1, 1, "m");
    final AnalysisFinding laterLine = finding("ward:java/a", "a.java", 9, 1, "m");
    final AnalysisFinding laterColumn = finding("ward:java/a", "a.java", 2, 5, "m");
    final AnalysisFinding laterRule = finding("ward:java/b", "a.java", 2, 5, "m");
    final AnalysisFinding laterMessage = finding("ward:java/b", "a.java", 2, 5, "n");
    final List<AnalysisFinding> findings =
        new ArrayList<>(List.of(laterMessage, late, laterRule, laterColumn, laterLine));

    findings.sort(AnalysisFindingOrder.ORDER);

    assertThat(findings).containsExactly(laterColumn, laterRule, laterMessage, laterLine, late);
  }

  @Test
  void findingsWithoutALocationSortFirst() {
    final AnalysisFinding located = finding("ward:java/a", "a.java", 1, 1, "m");
    final AnalysisFinding unlocated =
        new AnalysisFinding(
            "ward:java/a", AnalysisSeverity.ERROR, "m", null, null, null, null, List.of());
    final List<AnalysisFinding> findings = new ArrayList<>(List.of(located, unlocated));

    findings.sort(AnalysisFindingOrder.ORDER);

    assertThat(findings).containsExactly(unlocated, located);
  }

  private static AnalysisFinding finding(
      final String ruleId,
      final String file,
      final int line,
      final int column,
      final String message) {
    return new AnalysisFinding(
        ruleId, AnalysisSeverity.ERROR, message, file, line, column, null, List.of());
  }
}
