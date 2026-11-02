package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisFindingTest {

  @Test
  void allowsMissingLocation() {
    final AnalysisFinding finding =
        new AnalysisFinding("R1", AnalysisSeverity.NOTE, "msg", null, null, null, null);

    assertThat(finding.file()).isNull();
    assertThat(finding.line()).isNull();
  }

  @Test
  void requiresRuleSeverityAndMessage() {
    assertThatThrownBy(
            () -> new AnalysisFinding(null, AnalysisSeverity.NOTE, "m", null, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AnalysisFinding("R", null, "m", null, null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new AnalysisFinding("R", AnalysisSeverity.NOTE, null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNonPositiveLocation() {
    assertThatThrownBy(
            () -> new AnalysisFinding("R", AnalysisSeverity.ERROR, "m", "a.java", 0, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
