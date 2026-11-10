package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that the taint check needs the data-flow graphs, as the unused-assignment check does. */
class TaintCheckTest {

  private static final SyntaxNode ROOT = null;

  private final TaintCheck check =
      new TaintCheck(new TaintSpec(List.of(), List.of(), List.of(), List.of()), "m");

  /** Without the graphs the check cannot run, so it fails rather than reporting nothing. */
  @Test
  void failsWithoutGraphs() {
    assertThatThrownBy(() -> check.matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class)
        .hasMessageContaining("data-flow graphs");
  }

  /** A rule that reads no graphs has nothing to report. */
  @Test
  void reportsNothingWithoutGraphs() {
    assertThat(check.matches(ROOT, FlowView.NONE)).isEmpty();
  }
}
